package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.*;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionReportService {
    private final InspectionAccessService access;
    private final InspectionTaskService tasks;
    private final InspectionReportRevisionMapper reports;
    private final InspectionReportResultMapper reportResults;
    private final InspectionReportFileMapper reportFiles;
    private final InspectionDetectionResultMapper results;
    private final InspectionTaskRuleSnapshotMapper snapshots;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionExecutionMapper executions;
    private final InspectionAttemptMapper attempts;
    private final InspectionRuleQueryService rules;
    private final InspectionFiles files;
    private final FileEvidenceApi evidence;
    private final NativeGeneratedFileApi generated;
    private final PlatformDeliveryMaterialApi materials;
    private final InspectionReportRenderer renderer = new InspectionReportRenderer();
    private final InspectionReportFileValidator validator = new InspectionReportFileValidator();

    public record Generate(InspectionRequestContext context, Long taskId, String explanation) { }
    public record ManualFile(String formatCode, InspectionFiles.Reference file) { }
    public record Manual(InspectionRequestContext context, Long taskId, String explanation, List<ManualFile> files) { }
    public record ReportGate(InspectionRequestContext context, InspectionTaskDO task, Long reportId) { }
    private record ReportInput(InspectionTaskDO task, InspectionReportRevisionDO report, List<InspectionDetectionResultDO> results) { }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary generate(Generate command) {
        var task = writable(command.context(), command.taskId()); var input = inputs(task);
        var report = create(new Creation(command.context(), task, "PLATFORM", command.explanation(), input));
        List<InspectionReportRenderer.Rendered> rendered = new ArrayList<>();
        try {
            var context = renderContext(new ReportInput(task, report, input));
            for (var format : InspectionReportRenderer.Format.values()) rendered.add(renderer.render(new InspectionReportRenderer.RenderRequest(context, format)));
        } catch (RuntimeException failure) {
            report.setStatusCode("FAILED"); report.setFailureMessage("平台报告渲染失败，可重试或提交人工报告"); reports.updateById(report);
            tasks.save(new InspectionTaskService.Save(command.context(), task, "REPORT_GENERATION_FAILED", task.getStatusCode(), report.getFailureMessage()));
            return InspectionTaskService.summary(task);
        }
        for (int index = 0; index < rendered.size(); index++) {
            var file = rendered.get(index); String format = InspectionReportRenderer.Format.values()[index].name();
            var stored = generated.create(new NativeGeneratedFileCommand(task.getTenantId(), command.context().actorId(),
                    "inspection-report-" + report.getId() + "-" + format, "SRV", "INSPECTION_TASK", task.getId(), task.getVersion(),
                    "REPORT", "INSPECTION_REPORT", file.fileName(), file.mediaType(), file.content()));
            var document = evidence.inspectDocument(task.getTenantId(), stored.referenceId());
            require(document != null && document.available(), "报告文件引用不可用");
            var row = fileRow(new FileCreation(task, report, format, stored.artifactId(), stored.versionNo(), document.referenceKey(),
                    stored.fileName(), stored.sha256(), stored.materialId())); reportFiles.insert(row);
        }
        ready(new Ready(command.context(), task, report)); return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary manual(Manual command) {
        var task = writable(command.context(), command.taskId());
        InspectionTaskService.validateText(command.explanation(), 2000, "人工报告说明");
        require(command.files() != null && !command.files().isEmpty() && command.files().size() <= 4, "请提交至少一种有效报告格式");
        Set<String> formats = new HashSet<>(); Set<InspectionFiles.Reference> references = new HashSet<>();
        List<FileArtifactVersionFact> facts = new ArrayList<>();
        for (var file : command.files()) {
            require(file != null && Set.of("PDF", "DOCX", "DOC", "XML").contains(file.formatCode())
                    && formats.add(file.formatCode()) && references.add(file.file()), "报告格式或文件引用重复/无效");
            var query = new InspectionFiles.Query(task.getId(), "REPORT", file.file()); var fact = files.lock(query);
            try { validator.validate(new InspectionReportFileValidator.Input(file.formatCode(), fact.name(), fact.mediaType(), files.read(query))); }
            catch (IllegalArgumentException invalid) { throw new InspectionException(InspectionException.Reason.FILE_INVALID, invalid.getMessage()); }
            facts.add(fact);
        }
        var report = create(new Creation(command.context(), task, "MANUAL", command.explanation(), inputs(task)));
        for (int index = 0; index < facts.size(); index++) {
            var fact = facts.get(index);
            var row = fileRow(new FileCreation(task, report, command.files().get(index).formatCode(), fact.artifactId(), fact.versionNo(),
                    fact.referenceKey(), fact.name(), fact.sha256(), null)); reportFiles.insert(row);
            row.setMaterialId(materials.registerNativeSourceFile(fact)); reportFiles.updateById(row);
        }
        ready(new Ready(command.context(), task, report)); return InspectionTaskService.summary(task);
    }

    public boolean available(ReportGate gate) {
        var report = reports.selectById(gate.reportId());
        if (report == null || !Objects.equals(report.getTenantId(), gate.task().getTenantId())
                || !Objects.equals(report.getTaskId(), gate.task().getId()) || !"AVAILABLE".equals(report.getStatusCode())) return false;
        var rows = reportFiles.selectForTask(new InspectionTaskRows(report.getTenantId(), report.getTaskId())).stream()
                .filter(row -> Objects.equals(row.getReportId(), report.getId())).toList();
        if (rows.isEmpty()) return false;
        for (var row : rows) {
            var fact = evidence.lockAndRevalidate(new FileEvidenceApi.Query(report.getTenantId(), row.getArtifactId(), row.getFileVersionNo(),
                    "SRV", "INSPECTION_TASK", report.getTaskId().toString(), "REPORT", row.getReferenceKey(), row.getSha256()));
            if (fact == null || !fact.valid()) return false;
        }
        return "MANUAL".equals(report.getSourceCode()) || rows.stream().map(InspectionReportFileDO::getFormatCode)
                .collect(Collectors.toSet()).containsAll(Set.of("PDF", "DOCX", "XML"));
    }

    private record Creation(InspectionRequestContext context, InspectionTaskDO task, String source, String explanation,
                            List<InspectionDetectionResultDO> input) { }
    private InspectionReportRevisionDO create(Creation creation) {
        var task = creation.task(); var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        var report = new InspectionReportRevisionDO(); report.setTenantId(task.getTenantId()); report.setVersion(1L);
        report.setTaskId(task.getId()); report.setAttemptId(task.getCurrentAttemptId());
        report.setRevisionNo(reports.selectForTask(rows).stream().mapToInt(InspectionReportRevisionDO::getRevisionNo).max().orElse(0) + 1);
        report.setSourceCode(creation.source()); report.setStatusCode("GENERATING");
        report.setTemplateVersion("PLATFORM".equals(creation.source()) ? "inspection-basic-v1" : null);
        report.setInputDigest(DigestUtil.sha256Hex(JsonUtils.toJsonString(creation.input())));
        report.setExplanation(creation.explanation()); report.setGeneratedBy(creation.context().actorId()); report.setGeneratedAt(LocalDateTime.now());
        reports.insert(report);
        for (var result : creation.input()) {
            var row = new InspectionReportResultDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
            row.setTaskId(task.getId()); row.setReportId(report.getId()); row.setResultId(result.getId()); reportResults.insert(row);
        }
        return report;
    }
    private record Ready(InspectionRequestContext context, InspectionTaskDO task, InspectionReportRevisionDO report) { }
    private void ready(Ready ready) {
        ready.report().setStatusCode("AVAILABLE"); reports.updateById(ready.report());
        boolean available = available(new ReportGate(ready.context(), ready.task(), ready.report().getId()));
        ready.task().setCurrentReportId(ready.report().getId());
        tasks.transition(new InspectionTaskService.Change(ready.context(), ready.task(), new InspectionTaskStateMachine.Transition(
                ready.task().getStatusCode(), InspectionTaskStateMachine.Action.REPORT_READY, true, available, false, 0, false, false),
                "形成报告V" + ready.report().getRevisionNo()));
    }
    private record FileCreation(InspectionTaskDO task, InspectionReportRevisionDO report, String format, Long artifactId,
                                Integer versionNo, String referenceKey, String name, String sha256, Long materialId) { }
    private InspectionReportFileDO fileRow(FileCreation input) {
        var row = new InspectionReportFileDO(); row.setTenantId(input.task().getTenantId()); row.setVersion(1L);
        row.setTaskId(input.task().getId()); row.setReportId(input.report().getId()); row.setFormatCode(input.format());
        row.setArtifactId(input.artifactId()); row.setFileVersionNo(input.versionNo()); row.setReferenceKey(input.referenceKey());
        row.setFileName(input.name()); row.setSha256(input.sha256()); row.setMaterialId(input.materialId()); return row;
    }
    private InspectionTaskDO writable(InspectionRequestContext context, Long taskId) {
        var task = access.task(new InspectionAccessService.TaskAccess(context, taskId, "pms:inspection-report:generate", true, false));
        require(Set.of("REPORT_PENDING", "ANNOTATION_PENDING").contains(task.getStatusCode()), "当前阶段不能生成报告"); return task;
    }
    private List<InspectionDetectionResultDO> inputs(InspectionTaskDO task) {
        require(task.getCurrentAttemptId() != null, "尚无完成的执行尝试");
        var attempt = attempts.selectById(task.getCurrentAttemptId());
        require(attempt != null && "COMPLETED".equals(attempt.getStatusCode()), "执行尝试未完成");
        var current = results.selectCurrent(new InspectionAttemptRows(task.getTenantId(), task.getCurrentAttemptId()));
        long count = snapshots.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(row -> Objects.equals(row.getAttemptId(), task.getCurrentAttemptId())).count();
        require(count > 0 && current.size() == count && current.stream().allMatch(row -> Set.of("PASS", "ABNORMAL").contains(row.getOutcome())),
                "检测结果不完整，不能生成有效报告"); return current;
    }
    private InspectionReportRenderer.ReportContext renderContext(ReportInput input) {
        var task = input.task(); var query = new InspectionTaskRows(task.getTenantId(), task.getId());
        var deviceMap = devices.selectForTask(query).stream().collect(Collectors.toMap(InspectionTaskDeviceDO::getDeviceId, d -> d));
        var executionMap = executions.selectForTask(query).stream().collect(Collectors.toMap(InspectionExecutionDO::getId, e -> e));
        var rows = input.results().stream().map(result -> {
            var rule = rules.detail(result.getRuleRevisionId()); var device = deviceMap.get(result.getDeviceId()); var execution = executionMap.get(result.getExecutionId());
            String detail = "设备型号：" + Objects.toString(device.getProductModel(), "未记录") + "；软件版本："
                    + Objects.toString(execution.getSoftwareVersion(), "未识别") + "\n检测ID：" + rule.getDetectionId() + " / 规则V" + rule.getRevisionNo()
                    + "\n命令：" + rule.getCommands().stream().sorted(Comparator.comparingInt(c -> c.executionOrder())).map(c -> c.commandContent()).collect(Collectors.joining("\n"))
                    + "\n预期正则：" + rule.getExpectedResultRegex() + "\n预期阈值：" + rule.getThresholdOperator() + rule.getThresholdValue() + rule.getThresholdUnit()
                    + "\n实际输出：" + Objects.toString(result.getOutputSummary(), "未提供") + "\n判定说明：" + Objects.toString(result.getDetailMessage(), "")
                    + "\n平台登记人ID：" + execution.getSubmittedBy() + "；登记时间：" + execution.getSubmittedAt()
                    + ("OFFLINE".equals(execution.getPurposeCode()) ? "；离线日志不证明实际设备执行时间"
                    : "\n平台采集：" + execution.getCollectionTaskId() + " / 结果版本" + execution.getCollectionResultVersion()
                    + "；设备执行开始：" + Objects.toString(execution.getDeviceStartedAt(), "未提供")
                    + "；设备执行结束：" + Objects.toString(execution.getDeviceCompletedAt(), "未提供"));
            return new InspectionReportRenderer.ResultRow(device.getSerialNumber(), rule.getInspectionItem(), rule.getCategoryNameSnapshot(),
                    rule.getSeverityNameSnapshot(), "PASS".equals(result.getOutcome()) ? "通过" : "异常",
                    result.getMeasuredValue() == null ? "未取得唯一数值" : result.getMeasuredValue().toPlainString() + result.getMeasuredUnit(), detail);
        }).toList();
        return InspectionReportRenderer.ReportContext.builder().taskCode(task.getTaskCode()).taskName(task.getTaskName())
                .projectName(task.getProjectCode() + " " + task.getProjectName()).customerName(task.getCustomerName())
                .mode("OFFLINE".equals(attempts.selectById(input.report().getAttemptId()).getMode()) ? "离线巡检" : "在线巡检")
                .revision(input.report().getRevisionNo()).generatedAt(input.report().getGeneratedAt().toString()).results(rows).build();
    }
}
