package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionReportContentQuery;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.umc.InspectionUmcReportResults.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** Short transactions only. UMC and DAC requests are executed by the orchestration service outside these methods. */
@Service
@RequiredArgsConstructor
public class InspectionUmcTransactions {
    public static final String PURPOSE = "UMC_REPORT";
    private final InspectionAccessService access;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionAttemptMapper attempts;
    private final InspectionExecutionMapper executions;
    private final InspectionReportRevisionMapper reports;
    private final InspectionReportFileMapper reportFiles;
    private final NativeGeneratedFileApi generated;
    private final FileEvidenceApi evidence;
    private final AuthenticatedFileContentApi content;

    public record Access(InspectionRequestContext context, Long taskId, Long deviceId) { }
    public record Scope(InspectionTaskDO task, InspectionTaskDeviceDO device) { }
    public record Prepare(Access access, String host, String originalDigest, String commandHint, Long executionId) {
        public Prepare(Access access,String host,String originalDigest,String commandHint) { this(access,host,originalDigest,commandHint,null); }
    }
    public record Reference(Long reportId) { }
    public record BatchLogs(InspectionRequestContext context, Long taskId, Long attemptId, String digest) { }
    public record ExecutionAccess(InspectionRequestContext context, Long taskId, Long executionId) { }

    @Transactional(rollbackFor = Exception.class)
    public Reference findExecutionReport(ExecutionAccess request) {
        var task = access.task(new InspectionAccessService.TaskAccess(request.context(), request.taskId(), "pms:inspection-task:execute", true, false));
        var execution = executions.selectById(request.executionId());
        require(execution != null && Objects.equals(execution.getTenantId(), task.getTenantId())
                && Objects.equals(execution.getTaskId(), task.getId()) && Objects.equals(execution.getAttemptId(), task.getCurrentAttemptId()), "执行记录不属于当前尝试");
        var found = reports.selectUmcSources(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(r -> Objects.equals(InspectionUmcReportResults.source(r).executionId(), execution.getId())).toList();
        require(found.size() <= 1, "执行记录关联了多个UMC结果，请核对来源");
        return found.isEmpty() ? null : new Reference(found.getFirst().getId());
    }

    public InspectionViews.TaskSummary executionSummary(ExecutionAccess request) {
        return InspectionTaskService.summary(readable(request.context(), request.taskId()));
    }
    @Transactional(rollbackFor = Exception.class)
    public List<Long> uploadedDevices(BatchLogs query) {
        var task = writable(query.context(), query.taskId());
        require(Objects.equals(task.getCurrentAttemptId(), query.attemptId()), "离线日志不属于当前尝试");
        return executions.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(e -> Objects.equals(e.getAttemptId(), query.attemptId()) && Objects.equals(e.getOriginalFileSha256(), query.digest())
                        && "OFFLINE".equals(e.getPurposeCode()) && !Set.of("FAILED", "CANCELLED").contains(e.getStatusCode()))
                .map(InspectionExecutionDO::getDeviceId).distinct().toList();
    }
    public record ReportAccess(InspectionRequestContext context, Long taskId, Long reportId) { }
    public record Invocation(Reference reference, Long attemptId, String uploadName, byte[] log, Source state, boolean queryOnly) { }
    public record Complete(ReportAccess access, UmcInspectionApi.Result result) { }
    public record Failed(ReportAccess access, UmcInspectionApi.Checkpoint checkpoint, String code) { }
    public record View(Long id, Long attemptId, Integer revisionNo, String statusCode, String failureMessage,
                       LocalDateTime generatedAt, List<InspectionViews.ReportFileView> files, UmcInspectionApi.Report detail) { }
    private record FileWrite(InspectionRequestContext context, InspectionTaskDO task, InspectionReportRevisionDO report,
                             String format, String name, String mediaType, byte[] bytes) { }
    private record StoredFile(NativeGeneratedFileApi.RegisteredFile file, FileEvidenceApi.Document document) { }
    private record ViewQuery(InspectionTaskDO task, InspectionReportRevisionDO report, boolean includeDetail) { }

    @Transactional(rollbackFor = Exception.class)
    public Scope validate(Access request) {
        var task = writable(request.context(), request.taskId());
        require(task.getCurrentAttemptId() != null, "请先开始一次巡检");
        var attempt = attempts.selectById(task.getCurrentAttemptId());
        require(attempt != null && Objects.equals(attempt.getTenantId(), task.getTenantId())
                && Objects.equals(attempt.getTaskId(), task.getId()) && ("OFFLINE".equals(attempt.getMode())
                || "ONLINE".equals(attempt.getMode()) && "UMC".equals(attempt.getProviderCode())), "当前尝试不能提交UMC日志");
        var device = devices.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(d -> Objects.equals(d.getDeviceId(), request.deviceId())).findFirst().orElseThrow(
                        () -> new InspectionException(InspectionException.Reason.FORBIDDEN, "设备不属于本巡检任务"));
        require(device.getSerialNumber() != null && !device.getSerialNumber().isBlank(), "所选设备缺少可核对的序列号");
        return new Scope(task, device);
    }

    @Transactional(rollbackFor = Exception.class)
    public Reference prepare(Prepare command) {
        var scope = validate(command.access()); var task = scope.task();
        var query = new InspectionTaskRows(task.getTenantId(), task.getId());
        var matching = executions.selectForTask(query).stream().filter(e -> Objects.equals(e.getAttemptId(), task.getCurrentAttemptId())
                && Objects.equals(e.getDeviceId(), command.access().deviceId()) && Objects.equals(e.getOriginalFileSha256(), command.originalDigest())
                && Objects.equals(e.getCommandHint(), command.commandHint())
                && (command.executionId() == null ? "OFFLINE".equals(e.getPurposeCode())
                    : Objects.equals(command.executionId(),e.getId()) && "COLLECT".equals(e.getPurposeCode()))
                && !Set.of("FAILED", "CANCELLED").contains(e.getStatusCode()) && e.getArtifactId() != null).toList();
        require(matching.size() == 1, "未找到唯一可用的本次离线日志，请核对原执行记录");
        var execution = matching.getFirst();
        require(scope.device().getSerialNumber().equalsIgnoreCase(Objects.toString(execution.getRecognizedSerial(), "")), "日志序列号与设备不一致");
        for (var old : reports.selectUmcSources(query)) {
            var source = InspectionUmcReportResults.source(old);
            if (Objects.equals(execution.getId(), source.executionId())) {
                require(command.host().equals(source.host()), "相同日志的来源IP已冻结，请使用原记录恢复");
                return new Reference(old.getId());
            }
        }
        var storedLog = new InspectionUmcReportResults.StoredLog(execution.getArtifactId(), execution.getFileVersionNo(),
                execution.getFileReferenceKey(), execution.getFileSha256());
        byte[] redactedLog = UmcLogDocument.fromNormalized(new UmcLogDocument.NormalizedInput("NPDMS-" + scope.device().getDeviceId(), read(new LogRead(task, storedLog, "LOG"))));
        var report = new InspectionReportRevisionDO();
        report.setTenantId(task.getTenantId()); report.setVersion(1L); report.setTaskId(task.getId());
        report.setAttemptId(task.getCurrentAttemptId());
        report.setRevisionNo(reports.selectForTask(query).stream().mapToInt(InspectionReportRevisionDO::getRevisionNo).max().orElse(0) + 1);
        report.setSourceCode("UMC"); report.setStatusCode("GENERATING"); report.setTemplateVersion("umc-doc-v1");
        report.setInputDigest(DigestUtil.sha256Hex(redactedLog));
        report.setExplanation("UMC日志分析；原始日志SHA-256：" + command.originalDigest());
        report.setGeneratedBy(command.access().context().actorId()); report.setGeneratedAt(LocalDateTime.now());
        reports.insert(report);
        InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(report,
                new Source(scope.device().getDeviceId(), command.host(), scope.device().getSerialNumber(), false, null, storedLog, execution.getId())));
        saveReport(report);
        return new Reference(report.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Reference prepareCollection(ExecutionAccess request) {
        var task=writable(request.context(),request.taskId()); var execution=executions.selectById(request.executionId());
        var attempt=attempts.selectById(task.getCurrentAttemptId());
        require(attempt != null && "UMC".equals(attempt.getProviderCode()) && "ONLINE".equals(attempt.getMode()),"当前不是UMC在线尝试");
        require(execution != null && Objects.equals(execution.getTenantId(),task.getTenantId()) && Objects.equals(execution.getTaskId(),task.getId())
                && Objects.equals(execution.getAttemptId(),task.getCurrentAttemptId()) && "COLLECT".equals(execution.getPurposeCode())
                && execution.getArtifactId()!=null && execution.getCollectionResultVersion()!=null && execution.getCollectionFileVersionId()!=null
                && Boolean.TRUE.equals(execution.getCredentialSnapshotRecorded()),"请先完成本设备的DAC采集并保存已绑定日志");
        UmcLogDocument.requireIpv4(execution.getHost());
        return prepare(new Prepare(new Access(request.context(),task.getId(),execution.getDeviceId()),execution.getHost(),execution.getOriginalFileSha256(),execution.getCommandHint(),execution.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Invocation begin(ReportAccess request) {
        var task = writable(request.context(), request.taskId()); var report = report(request);
        require(!"AVAILABLE".equals(report.getStatusCode()), "UMC结果已经保存，请直接查看或下载");
        require(Objects.equals(task.getCurrentAttemptId(), report.getAttemptId()), "本次报告属于旧执行尝试，不能重新提交");
        var source = InspectionUmcReportResults.source(report);
        validate(new Access(request.context(), request.taskId(), source.deviceId()));
        byte[] log = read(new LogRead(task, source.log(), source.executionId() == null ? PURPOSE : "LOG"));
        if (source.executionId() != null) log = UmcLogDocument.fromNormalized(new UmcLogDocument.NormalizedInput("NPDMS-" + source.deviceId(), log));
        require(DigestUtil.sha256Hex(log).equals(report.getInputDigest()), "已保存日志摘要不一致");
        boolean queryOnly = source.dispatched();
        InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(report,
                new Source(source.deviceId(), source.host(), source.expectedSerial(), true, source.checkpoint(), source.log(), source.executionId())));
        report.setStatusCode("GENERATING"); report.setFailureMessage(null); saveReport(report);
        return new Invocation(new Reference(report.getId()), report.getAttemptId(), "NPDMS-" + source.deviceId() + "(" + source.host() + ")",
                log, source, queryOnly);
    }

    @Transactional(rollbackFor = Exception.class)
    public View complete(Complete command) {
        var request = command.access(); var task = writable(request.context(), request.taskId()); var report = report(request);
        if ("AVAILABLE".equals(report.getStatusCode())) return view(new ViewQuery(task, report, true));
        require(Objects.equals(task.getCurrentAttemptId(), report.getAttemptId()), "任务已开始新的执行尝试，未采用迟到的UMC结果");
        var source = InspectionUmcReportResults.source(report); var result = command.result();
        require(result.detail() != null && result.detail().devlist() != null && result.detail().devlist().size() == 1,
                "UMC返回设备数量与本次日志不一致");
        var device = result.detail().devlist().getFirst();
        require(source.host().equals(device.deviceip()) && source.expectedSerial().equalsIgnoreCase(Objects.toString(device.sn(), "")),
                "UMC识别的IP或序列号与本次设备不一致");
        require(DigestUtil.sha256Hex(result.doc()).equals(result.docSha256())
                && DigestUtil.sha256Hex(result.rawDetail()).equals(result.detailSha256()), "UMC结果摘要不一致");
        save(new FileWrite(request.context(), task, report, "DOC", task.getTaskCode()+"-巡检报告-"+report.getRevisionNo()+".doc", result.mediaType(), result.doc()));
        InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(report, result));
        report.setStatusCode("AVAILABLE"); report.setFailureMessage(null); saveReport(report);
        // The external record is archived in PLT; the task's canonical detection/state/active report is unchanged.
        return view(new ViewQuery(task, report, true));
    }

    @Transactional(rollbackFor = Exception.class)
    public View fail(Failed command) {
        var request = command.access(); var task = writable(request.context(), request.taskId()); var report = report(request);
        if ("AVAILABLE".equals(report.getStatusCode())) return view(new ViewQuery(task, report, true));
        var source = InspectionUmcReportResults.source(report);
        // A terminal worker checkpoint proves whether this invocation reached the upload boundary.
        // Process loss is normalized to UNKNOWN by the provider, even if an older NOT_SENT was observed.
        boolean noSubmission = !"TASK_CREATION_OUTCOME_UNKNOWN".equals(command.code())
                && (command.checkpoint() != null && "NOT_SENT".equals(command.checkpoint().uploadState())
                || Set.of("NOT_CONFIGURED", "LOGIN_FAILED", "INVALID_INPUT", "WORKER_UNAVAILABLE", "WORKER_START_FAILED").contains(command.code()));
        InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(report, new Source(source.deviceId(), source.host(), source.expectedSerial(),
                !noSubmission, command.checkpoint() == null ? source.checkpoint() : command.checkpoint(), source.log(), source.executionId())));
        report.setStatusCode("GENERATING"); report.setFailureMessage("UMC处理或保存失败（" + command.code() + "），可保留原记录重试");
        saveReport(report); return view(new ViewQuery(task, report, false));
    }

    public List<View> list(InspectionTaskQueryService.Detail request) {
        var task = readable(request.context(), request.taskId());
        return reports.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(r -> "UMC".equals(r.getSourceCode()) && !InspectionUmcBundle.matches(r)).sorted(Comparator.comparing(InspectionReportRevisionDO::getRevisionNo).reversed())
                .map(r -> view(new ViewQuery(task, r, false))).toList();
    }

    public View detail(ReportAccess request) {
        var task = readable(request.context(), request.taskId());
        return view(new ViewQuery(task, report(request), true));
    }

    private InspectionTaskDO readable(InspectionRequestContext context, Long taskId) {
        return access.task(new InspectionAccessService.TaskAccess(context, taskId, "pms:inspection-task:query", false, false));
    }
    private InspectionTaskDO writable(InspectionRequestContext context, Long taskId) {
        var task = access.task(new InspectionAccessService.TaskAccess(context, taskId, "pms:inspection-report:generate", true, false));
        access.permission(context, "pms:inspection-task:execute"); access.permission(context, "pms:file:upload");
        require(Set.of("EXECUTING", "REPORT_PENDING", "ANNOTATION_PENDING").contains(task.getStatusCode()), "当前阶段不能提交UMC日志");
        return task;
    }
    private InspectionReportRevisionDO report(ReportAccess request) {
        var row = reports.selectContent(new InspectionReportContentQuery(request.context().tenantId(), request.taskId(), request.reportId()));
        require(row != null && Objects.equals(row.getTenantId(), request.context().tenantId())
                && Objects.equals(row.getTaskId(), request.taskId()) && "UMC".equals(row.getSourceCode()), "UMC报告不存在或不属于当前任务");
        return row;
    }
    private record LogRead(InspectionTaskDO task, InspectionUmcReportResults.StoredLog file, String purpose) { }
    private byte[] read(LogRead query) {
        var task = query.task(); var file = query.file();
        byte[] bytes = content.read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(file.artifactId(),
                file.versionNo(), "SRV", "INSPECTION_TASK", task.getId().toString(), query.purpose(), file.referenceKey(),
                FileActionCodes.DOWNLOAD), 52_428_800));
        require(DigestUtil.sha256Hex(bytes).equals(file.sha256()), "UMC持久日志摘要不一致"); return bytes;
    }
    private void saveReport(InspectionReportRevisionDO report) {
        if (reports.updateById(report) != 1) throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "报告版本已变更，请刷新后重试");
    }
    private StoredFile storeFile(FileWrite write) {
        String operation = "umc-" + write.report().getId() + "-" + write.format() + "-" + write.task().getVersion() + "-" + DigestUtil.sha256Hex(write.bytes());
        var stored = generated.create(new NativeGeneratedFileCommand(write.task().getTenantId(), write.context().actorId(), operation,
                "SRV", "INSPECTION_TASK", write.task().getId(), write.task().getVersion(), PURPOSE, "INSPECTION_REPORT",
                write.name(), write.mediaType(), write.bytes()));
        var document = evidence.inspectDocument(write.task().getTenantId(), stored.referenceId());
        require(document != null && document.available(), "PLT未确认UMC文件可用");
        return new StoredFile(stored, document);
    }
    private void save(FileWrite write) {
        var file = storeFile(write); var stored = file.file(); var document = file.document();
        var existing = reportFiles.selectForTask(new InspectionTaskRows(write.task().getTenantId(), write.task().getId())).stream()
                .filter(f -> Objects.equals(f.getReportId(), write.report().getId()) && write.format().equals(f.getFormatCode())).findFirst();
        var row = existing.orElseGet(InspectionReportFileDO::new);
        if (existing.isEmpty()) { row.setTenantId(write.task().getTenantId()); row.setVersion(1L); row.setTaskId(write.task().getId()); row.setReportId(write.report().getId()); }
        row.setFormatCode(write.format()); row.setArtifactId(stored.artifactId()); row.setFileVersionNo(stored.versionNo());
        row.setReferenceKey(document.referenceKey()); row.setMaterialId(stored.materialId()); row.setFileName(stored.fileName()); row.setSha256(stored.sha256());
        if (existing.isEmpty()) reportFiles.insert(row); else reportFiles.updateById(row);
    }
    /** Query side reads the local business data; it has no UMC dependency. */
    private View view(ViewQuery query) {
        var report = query.report();
        var rows = reportFiles.selectForTask(new InspectionTaskRows(report.getTenantId(), report.getTaskId())).stream()
                .filter(f -> Objects.equals(f.getReportId(), report.getId())).toList();
        var files = rows.stream().filter(f -> "DOC".equals(f.getFormatCode()))
                .map(f -> cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(f, InspectionViews.ReportFileView.class)).toList();
        UmcInspectionApi.Report detail = null;
        if (query.includeDetail() && "AVAILABLE".equals(report.getStatusCode())) {
            detail = InspectionUmcReportResults.load(report);
        }
        return new View(report.getId(), report.getAttemptId(), report.getRevisionNo(), report.getStatusCode(), InspectionUmcReportResults.currentFailure(report),
                report.getGeneratedAt(), files, detail);
    }
}
