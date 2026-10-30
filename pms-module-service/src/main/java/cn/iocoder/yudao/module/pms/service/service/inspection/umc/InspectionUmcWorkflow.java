package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionReportContentQuery;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** UMC workflow adapter; legacy platform judgement services and their rule snapshots remain intact. */
@Service
@RequiredArgsConstructor
public class InspectionUmcWorkflow {
    private final InspectionAccessService access;
    private final InspectionTaskService tasks;
    private final InspectionAttemptMapper attempts;
    private final InspectionExecutionMapper executions;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionDetectionResultMapper results;
    private final InspectionReportRevisionMapper reports;
    private final InspectionReportResultMapper reportInputs;
    private final InspectionUmcFormalResults facts;
    private final InspectionUmcBundle bundles;
    public record Command(InspectionRequestContext context, Long taskId, String explanation) { }
    public record Fetch(InspectionRequestContext context, Long taskId, Long executionId) { }
    public record Uploaded(InspectionRequestContext context, Long taskId, String digest) { }
    public record UploadSource(InspectionRequestContext context, Long taskId, Long attemptId, boolean hasHosts) { }
    public record NamedUpload(InspectionRequestContext context, Long taskId, Long attemptId, String fileName) { }
    private record Inputs(InspectionTaskDO task, List<InspectionDetectionResultDO> results) { }
    private record VerifiedInputs(List<InspectionDetectionResultDO> results, List<Long> reportIds) { }
    private record Starting(Command command, String mode, InspectionTaskStateMachine.Action action) { }
    public String inferUploadSource(NamedUpload command) {
        var attempt = attempts.selectById(command.attemptId());
        if (attempt == null || !Objects.equals(attempt.getTenantId(), command.context().tenantId())
                || !Objects.equals(attempt.getTaskId(), command.taskId()) || !"UMC".equals(attempt.getProviderCode())) return null;
        return UmcLogDocument.sourceHostFromFileName(command.fileName());
    }
    public List<InspectionUmcService.Host> inferBatchUploadSources(NamedUpload command) {
        var host = inferUploadSource(command);
        if (host == null) return null;
        var selected = devices.selectForTask(new InspectionTaskRows(command.context().tenantId(), command.taskId()));
        return selected.size() == 1 ? List.of(new InspectionUmcService.Host(selected.getFirst().getDeviceId(), host)) : null;
    }
    public void validateUploadSource(UploadSource command) {
        var attempt=attempts.selectById(command.attemptId());
        if (attempt != null && Objects.equals(attempt.getTenantId(),command.context().tenantId()) && Objects.equals(attempt.getTaskId(),command.taskId()) && "UMC".equals(attempt.getProviderCode()))
            require(command.hasHosts(),"未识别到设备来源IPv4，请填写来源IP后上传，日志尚未上传");
    }

    /** Register available judgements as part of the upload flow, after all remote calls have returned. */
    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary captureUploaded(Uploaded command) {
        var task=access.task(new InspectionAccessService.TaskAccess(command.context(),command.taskId(),"pms:inspection-task:execute",true,false));
        var current=attempts.selectById(task.getCurrentAttemptId());
        if (current==null || !"UMC".equals(current.getProviderCode())) return InspectionTaskService.summary(task);
        var available=reports.selectUmcExecutionResults(new InspectionTaskRows(task.getTenantId(),task.getId())).stream()
                .filter(r->Objects.equals(r.getAttemptId(),task.getCurrentAttemptId()) && "AVAILABLE".equals(r.getStatusCode()))
                .map(r->InspectionUmcReportResults.source(r).executionId()).filter(Objects::nonNull).collect(Collectors.toSet());
        var uploaded=executions.selectForTask(new InspectionTaskRows(task.getTenantId(),task.getId())).stream()
                .filter(e->Objects.equals(e.getAttemptId(),task.getCurrentAttemptId()) && "OFFLINE".equals(e.getPurposeCode())
                        && Objects.equals(command.digest(),e.getOriginalFileSha256()) && !"CANCELLED".equals(e.getStatusCode()) && available.contains(e.getId())).toList();
        var summary=InspectionTaskService.summary(task);
        for (var execution:uploaded) {
            var context=new InspectionRequestContext(command.context().tenantId(),command.context().actorId(),command.context().idempotencyKey(),
                    command.context().correlationId(),summary.getVersion());
            summary=capture(new Fetch(context,task.getId(),execution.getId()));
        }
        return summary;
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary start(Command command) {
        return start(new Starting(command, "OFFLINE", InspectionTaskStateMachine.Action.START_OFFLINE));
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary startOnline(Command command) {
        return start(new Starting(command, "ONLINE", InspectionTaskStateMachine.Action.START_ONLINE));
    }

    private InspectionViews.TaskSummary start(Starting input) {
        var command = input.command();
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:execute", true, false));
        require(input.mode().equals(task.getMode()), "请使用与已保存巡检方式一致的UMC启动入口");
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        var history = attempts.selectForTask(rows);
        if (!history.isEmpty()) InspectionTaskService.validateText(command.explanation(), 2000, "新建UMC尝试原因");
        require(executions.selectForTask(rows).stream().noneMatch(e -> Objects.equals(e.getAttemptId(), task.getCurrentAttemptId())
                && Set.of("PENDING", "RUNNING", "COLLECTED", "CANCEL_REQUESTED").contains(e.getStatusCode())),
                "原尝试仍有未结束记录，请先在执行记录中明确停止采用，再新建UMC尝试");
        require(!devices.selectForTask(rows).isEmpty(), "尚未选择巡检设备");
        InspectionTaskStateMachine.next(new InspectionTaskStateMachine.Transition(task.getStatusCode(), input.action(),
                false, false, false, 0, false, false));
        for (var previous : history) if ("ACTIVE".equals(previous.getStatusCode())) {
            previous.setStatusCode("SUPERSEDED"); previous.setFinishedAt(LocalDateTime.now()); require(attempts.updateById(previous) == 1, "原尝试已变化，请刷新");
        }
        var attempt = new InspectionAttemptDO(); attempt.setTenantId(task.getTenantId()); attempt.setVersion(1L);
        attempt.setTaskId(task.getId()); attempt.setAttemptNo(history.stream().mapToInt(InspectionAttemptDO::getAttemptNo).max().orElse(0) + 1);
        attempt.setMode(input.mode()); attempt.setProviderCode("UMC"); attempt.setStatusCode("ACTIVE");
        attempt.setReason(command.explanation()); attempt.setStartedBy(command.context().actorId()); attempt.setStartedAt(LocalDateTime.now()); attempts.insert(attempt);
        if ("ONLINE".equals(input.mode())) tasks.freezeCollectionCommands(new InspectionTaskService.CollectionCommands(command.context(), task, attempt));
        task.setCurrentAttemptId(attempt.getId());
        tasks.transition(new InspectionTaskService.Change(command.context(), task, new InspectionTaskStateMachine.Transition(task.getStatusCode(),
                input.action(), false, false, false, 0, false, false), "开始UMC正式判定（" + input.mode() + "）：" + Objects.toString(command.explanation(), "首次执行")));
        return InspectionTaskService.summary(task);
    }

    public InspectionUmcTransactions.ExecutionAccess collection(Fetch command) {
        var task=access.task(new InspectionAccessService.TaskAccess(command.context(),command.taskId(),"pms:inspection-task:execute",false,false));
        if (!"ONLINE".equals(attempt(task).getMode())) return null;
        return new InspectionUmcTransactions.ExecutionAccess(command.context(),command.taskId(),command.executionId());
    }

    /** Read-only preparation for remote recovery: no long transaction or version write is held while UMC runs. */
    public InspectionUmcTransactions.ReportAccess source(Fetch command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:execute", false, false));
        attempt(task);
        var execution = executions.selectById(command.executionId());
        require(execution != null && Objects.equals(execution.getTenantId(), task.getTenantId()) && Objects.equals(execution.getTaskId(), task.getId())
                && Objects.equals(execution.getAttemptId(), task.getCurrentAttemptId()) && !"CANCELLED".equals(execution.getStatusCode()), "UMC执行记录不属于当前尝试或已停止采用");
        var source = reports.selectUmcSources(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(r -> Objects.equals(InspectionUmcReportResults.source(r).executionId(), execution.getId())).toList();
        require(source.size() == 1, "该执行记录尚未关联唯一UMC报告，请先完成原日志上传");
        return new InspectionUmcTransactions.ReportAccess(command.context(), task.getId(), source.getFirst().getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary capture(Fetch command) {
        var task = writable(command.context(), command.taskId());
        var source = source(command); var report = reports.selectContent(new InspectionReportContentQuery(task.getTenantId(), task.getId(), source.reportId()));
        require(report != null, "UMC报告不存在");
        if (!"AVAILABLE".equals(report.getStatusCode())) return InspectionTaskService.summary(task);
        var observed = facts.observation(report); var execution = observed.execution(); var rules = observed.device().rulelist();
        require(rules != null && rules.stream().allMatch(r -> r != null && r.id() != null && !r.id().isBlank() && r.id().length() <= 64)
                && rules.stream().map(r -> r.id()).distinct().count() == rules.size(), "UMC规则来源标识缺失或重复");
        var existing = results.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(r -> Objects.equals(r.getSourceReportId(), report.getId())).collect(Collectors.toMap(InspectionDetectionResultDO::getExternalRuleId, r -> r));
        boolean changed = false;
        for (var rule : rules) {
            var old = existing.get(rule.id());
            if (old != null) { facts.rule(old); continue; }
            var row = new InspectionDetectionResultDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
            row.setTaskId(task.getId()); row.setAttemptId(task.getCurrentAttemptId()); row.setDeviceId(execution.getDeviceId()); row.setExecutionId(execution.getId());
            row.setSourceCode("UMC"); row.setSourceReportId(report.getId()); row.setExternalRuleId(rule.id());
            row.setOutcome(InspectionUmcFormalResults.outcome(rule)); row.setDetailCode("UMC_FORMAL_RESULT"); results.insert(row); changed = true;
        }
        String state = "COMPLETE";
        if (!Objects.equals(execution.getStatusCode(), state) || (execution.getFailureCode() != null && !execution.getFailureCode().isBlank())
                || (execution.getFailureMessage() != null && !execution.getFailureMessage().isBlank())) {
            require(Set.of("PENDING", "RUNNING", "COMPLETE", "PARTIAL", "FAILED").contains(execution.getStatusCode()), "执行状态不能接收UMC正式判定");
            execution.setStatusCode(state); execution.setCompletedAt(LocalDateTime.now());
            execution.setSoftwareVersion(observed.device().version());
            // 取得并保存完整报告即完成执行；规则结果仍按原始报告逐项保留。
            execution.setFailureCode(""); execution.setFailureMessage("");
            require(executions.updateById(execution) == 1, "执行记录已变化，请刷新"); changed = true;
        }
        if (changed) tasks.save(new InspectionTaskService.Save(command.context(), task, "UMC_RESULT_CAPTURED", task.getStatusCode(), "保存执行" + execution.getId() + "的UMC正式结果引用"));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary finish(Command command) {
        var task = writable(command.context(), command.taskId()); inputs(new Inputs(task, results.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId()))));
        tasks.transition(new InspectionTaskService.Change(command.context(), task, new InspectionTaskStateMachine.Transition(task.getStatusCode(),
                InspectionTaskStateMachine.Action.EXECUTION_COMPLETE, true, false, false, 0, false, false), "全部设备UMC报告已完整获取并保存，进入报告阶段"));
        var attempt = attempt(task); attempt.setStatusCode("COMPLETED"); attempt.setFinishedAt(LocalDateTime.now()); require(attempts.updateById(attempt) == 1, "执行尝试已变化，请刷新");
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary adopt(Command command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-report:generate", true, false));
        require(Set.of("REPORT_PENDING", "ANNOTATION_PENDING").contains(task.getStatusCode()) && "COMPLETED".equals(attempt(task).getStatusCode()), "请先完成UMC执行");
        if (task.getCurrentReportId() != null) {
            var current = reports.selectContent(new InspectionReportContentQuery(task.getTenantId(), task.getId(), task.getCurrentReportId()));
            require(current != null && InspectionUmcBundle.matches(current) && Objects.equals(current.getAttemptId(), task.getCurrentAttemptId()), "当前正式报告来源不一致");
            require(bundles.available(new InspectionUmcBundle.Query(current)), "当前UMC正式报告已不可用"); return InspectionTaskService.summary(task);
        }
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId()); var verified = inputs(new Inputs(task, results.selectForTask(rows)));
        var selected = verified.results(); var sourceIds = verified.reportIds();
        var snapshot = new InspectionUmcBundle.Snapshot(sourceIds, selected.stream().map(InspectionDetectionResultDO::getId).sorted().toList());
        String json = JsonUtils.toJsonString(snapshot); var report = new InspectionReportRevisionDO(); report.setTenantId(task.getTenantId()); report.setVersion(1L);
        report.setTaskId(task.getId()); report.setAttemptId(task.getCurrentAttemptId()); report.setSourceCode("UMC"); report.setTemplateVersion(InspectionUmcBundle.TEMPLATE);
        report.setRevisionNo(reports.selectForTask(rows).stream().mapToInt(InspectionReportRevisionDO::getRevisionNo).max().orElse(0) + 1);
        report.setStatusCode("GENERATING"); report.setInputDigest(DigestUtil.sha256Hex(json)); report.setSourceContextJson(json);
        report.setResultJson(json); report.setResultSha256(DigestUtil.sha256Hex(json)); report.setGeneratedAt(LocalDateTime.now()); report.setGeneratedBy(command.context().actorId());
        report.setExplanation("采用全部设备的UMC DOC原件及正式判定；" + Objects.toString(command.explanation(), "")); reports.insert(report);
        require(report.getId() != null && "GENERATING".equals(report.getStatusCode()) && !Boolean.TRUE.equals(report.getDeleted())
                && Objects.equals(report.getTenantId(), task.getTenantId()) && Objects.equals(report.getTaskId(), task.getId())
                && Objects.equals(report.getAttemptId(), task.getCurrentAttemptId()) && InspectionUmcBundle.matches(report),
                "UMC报告已冻结或来源范围不一致");
        require(selected.stream().map(InspectionDetectionResultDO::getId).distinct().count() == selected.size()
                && selected.stream().allMatch(input -> input.getId() != null && !Boolean.TRUE.equals(input.getDeleted())
                    && Objects.equals(input.getTenantId(), report.getTenantId()) && Objects.equals(input.getTaskId(), report.getTaskId())
                    && Objects.equals(input.getAttemptId(), report.getAttemptId()) && "UMC".equals(input.getSourceCode())
                    && Set.of("PASS", "ABNORMAL", "UNJUDGED", "ERROR").contains(input.getOutcome())
                    && input.getSourceReportId() != null && sourceIds.contains(input.getSourceReportId())
                    && input.getRuleSnapshotId() == null && input.getRuleRevisionId() == null), "UMC报告输入范围或判定来源不一致");
        for (var input : selected) {
            var link = new InspectionReportResultDO(); link.setTenantId(task.getTenantId()); link.setVersion(1L); link.setTaskId(task.getId());
            link.setReportId(report.getId()); link.setResultId(input.getId()); reportInputs.insert(link);
        }
        report.setStatusCode("AVAILABLE"); require(reports.updateById(report) == 1, "报告版本已变化");
        require(bundles.available(new InspectionUmcBundle.Query(report)), "UMC DOC原件未全部归档就绪");
        task.setCurrentReportId(report.getId());
        tasks.transition(new InspectionTaskService.Change(command.context(), task, new InspectionTaskStateMachine.Transition(task.getStatusCode(),
                InspectionTaskStateMachine.Action.REPORT_READY, true, true, false, 0, false, false), "采用全设备UMC报告，进入问题确认"));
        return InspectionTaskService.summary(task);
    }

    private InspectionTaskDO writable(InspectionRequestContext context, Long id) {
        var task = access.task(new InspectionAccessService.TaskAccess(context, id, "pms:inspection-task:execute", true, false));
        require("EXECUTING".equals(task.getStatusCode()), "当前不是执行阶段"); attempt(task); return task;
    }
    private InspectionAttemptDO attempt(InspectionTaskDO task) {
        var attempt = attempts.selectById(task.getCurrentAttemptId());
        require(attempt != null && Objects.equals(attempt.getTenantId(), task.getTenantId()) && Objects.equals(attempt.getTaskId(), task.getId())
                && Set.of("OFFLINE", "ONLINE").contains(attempt.getMode()) && "UMC".equals(attempt.getProviderCode()), "当前不是UMC正式尝试，请先新建UMC尝试"); return attempt;
    }
    private VerifiedInputs inputs(Inputs input) {
        var task = input.task(); var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        var active = executions.selectForTask(rows).stream().filter(e -> Objects.equals(e.getAttemptId(), task.getCurrentAttemptId()) && !"CANCELLED".equals(e.getStatusCode())).toList();
        require(active.stream().noneMatch(e -> Set.of("PENDING", "RUNNING", "COLLECTED", "CANCEL_REQUESTED").contains(e.getStatusCode())), "仍有设备日志未取得UMC正式结果");
        Map<Long, InspectionExecutionDO> latest = new HashMap<>();
        active.stream().filter(e -> !"PRECHECK".equals(e.getPurposeCode())).forEach(e -> latest.merge(e.getDeviceId(), e, (a, b) -> a.getId() > b.getId() ? a : b));
        var expected = devices.selectForTask(rows).stream().map(InspectionTaskDeviceDO::getDeviceId).collect(Collectors.toSet());
        // 既有记录的PARTIAL/FAILED可能来自旧规则结果门禁；是否完整以已保存报告和逐项来源核验为准，不改写旧执行。
        require(!expected.isEmpty() && latest.keySet().equals(expected)
                && latest.values().stream().allMatch(e -> Set.of("COMPLETE", "PARTIAL", "FAILED").contains(e.getStatusCode())),
                "每台设备均须取得并保存UMC报告");
        var selected = input.results().stream().filter(r -> "UMC".equals(r.getSourceCode()) && Objects.equals(r.getAttemptId(), task.getCurrentAttemptId())
                && latest.containsKey(r.getDeviceId()) && Objects.equals(latest.get(r.getDeviceId()).getId(), r.getExecutionId())).toList();
        selected.forEach(facts::rule);
        var sources = reports.selectUmcSources(rows); var sourceIds = new ArrayList<Long>();
        for (var execution : latest.values()) {
            var deviceResults = selected.stream().filter(r -> Objects.equals(r.getExecutionId(), execution.getId())).toList();
            var executionSources = sources.stream().filter(r -> Objects.equals(InspectionUmcReportResults.source(r).executionId(), execution.getId())).toList();
            require(executionSources.size() == 1, "UMC执行来源报告不唯一");
            var source = reports.selectContent(new InspectionReportContentQuery(task.getTenantId(), task.getId(), executionSources.getFirst().getId()));
            require(source != null && "AVAILABLE".equals(source.getStatusCode()), "UMC设备报告尚未完整保存");
            var observed = facts.observation(source); var rules = observed.device().rulelist();
            require(Objects.equals(observed.execution().getId(), execution.getId()) && rules != null
                    && rules.stream().allMatch(r -> r != null && r.id() != null && !r.id().isBlank() && r.id().length() <= 64)
                    && rules.stream().map(r -> r.id()).distinct().count() == rules.size(), "UMC规则来源标识缺失或重复");
            require(deviceResults.stream().allMatch(r -> Objects.equals(r.getSourceReportId(), source.getId()))
                    && deviceResults.size() == rules.size()
                    && deviceResults.stream().map(InspectionDetectionResultDO::getExternalRuleId).collect(Collectors.toSet())
                    .equals(rules.stream().map(r -> r.id()).collect(Collectors.toSet())), "UMC规则结果未全部登记");
            sourceIds.add(source.getId());
        }
        return new VerifiedInputs(selected, sourceIds.stream().distinct().sorted().toList());
    }
}
