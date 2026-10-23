package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi.Kind;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi.SourceReference;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionArchiveService {
    private final InspectionAccessService access;
    private final InspectionTaskService tasks;
    private final InspectionClosureFacts closure;
    private final CustomerInspectionHistoryApi customerHistory;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionExecutionMapper executions;
    private final InspectionDetectionResultMapper results;
    private final InspectionReportRevisionMapper reports;
    private final InspectionAnnotationMapper annotations;
    private final ServiceIssueMapper issues;
    private final ServiceIssueRemediationMapper remediations;
    private final InspectionEvidenceMapper evidence;
    private final FileEvidenceApi fileEvidence;
    public record Command(InspectionRequestContext context, Long taskId, String description) { }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary request(Command command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(),
                "pms:inspection-task:request-archive", true, false));
        require(Objects.equals(task.getOwnerUserId(), command.context().actorId()), "归档申请由本任务负责工程师提交");
        InspectionTaskService.validateText(command.description(), 2000, "归档申请说明");
        var facts = closure.current(new InspectionClosureFacts.Query(command.context(), task));
        validateEvidence(new InspectionTaskRows(task.getTenantId(), task.getId()));
        task.setArchiveRequestedBy(command.context().actorId()); task.setArchiveRequestedAt(LocalDateTime.now());
        tasks.transition(new InspectionTaskService.Change(command.context(), task,
                facts.transition(task, InspectionTaskStateMachine.Action.REQUEST_ARCHIVE), command.description()));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary archive(Command command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(),
                "pms:inspection-task:archive", true, true));
        InspectionTaskService.validateText(command.description(), 2000, "归档说明");
        var facts = closure.current(new InspectionClosureFacts.Query(command.context(), task));
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        require("CLOSED".equals(task.getStatusCode()) && task.getArchiveRequestedAt() != null
                && facts.reportAvailable() && facts.annotationsComplete() && facts.openIssues() == 0, "归档申请或业务闭环条件未满足");
        validateEvidence(rows);
        var reference = customerHistory.register(CustomerInspectionHistoryApi.ArchiveCommand.builder()
                .tenantId(task.getTenantId()).actorId(command.context().actorId()).customerId(task.getCustomerId())
                .customerCode(task.getCustomerCode()).projectId(task.getProjectId()).taskId(task.getId())
                .archivedTaskVersion(task.getVersion() + 1).taskCode(task.getTaskCode()).taskName(task.getTaskName())
                .reportId(task.getCurrentReportId()).references(sourceReferences(rows)).build());
        require(reference != null && reference.id() != null && Objects.equals(reference.taskId(), task.getId())
                && Objects.equals(reference.taskVersion(), task.getVersion() + 1), "客户资产域未确认本次归档引用");
        task.setCustomerArchiveId(reference.id()); task.setArchivedBy(command.context().actorId()); task.setArchivedAt(LocalDateTime.now());
        tasks.transition(new InspectionTaskService.Change(command.context(), task,
                facts.transition(task, InspectionTaskStateMachine.Action.ARCHIVE), command.description()));
        return InspectionTaskService.summary(task);
    }

    private void validateEvidence(InspectionTaskRows rows) {
        var notes = remediations.selectForTask(rows); var files = evidence.selectForTask(rows);
        for (var issue : issues.selectForTask(rows)) {
            require("CLOSED".equals(issue.getStatusCode()) && issue.getClosedBy() != null && issue.getClosedAt() != null,
                    "仍有未确认闭环的跟踪事项");
            var processes = notes.stream().filter(n -> Objects.equals(n.getIssueId(), issue.getId()) && "PROCESS".equals(n.getActionCode()))
                    .map(n -> n.getId()).collect(Collectors.toSet());
            require(files.stream().anyMatch(f -> processes.contains(f.getRemediationId())), "闭环处理证据缺失");
        }
        for (var row : files) {
            var fact = fileEvidence.lockAndRevalidate(new FileEvidenceApi.Query(row.getTenantId(), row.getArtifactId(), row.getFileVersionNo(),
                    "SRV", "INSPECTION_TASK", row.getTaskId().toString(), "EVIDENCE", row.getReferenceKey(), row.getSha256()));
            require(fact != null && fact.valid(), "历史处理证据已不可用，不能归档");
        }
    }
    private List<SourceReference> sourceReferences(InspectionTaskRows rows) {
        var refs = new ArrayList<SourceReference>();
        devices.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.DEVICE, r.getDeviceId())));
        executions.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.EXECUTION, r.getId())));
        results.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.RESULT, r.getId())));
        reports.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.REPORT, r.getId())));
        annotations.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.ANNOTATION, r.getId())));
        issues.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.ISSUE, r.getId())));
        remediations.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.REMEDIATION, r.getId())));
        evidence.selectForTask(rows).forEach(r -> refs.add(new SourceReference(Kind.EVIDENCE, r.getId())));
        return List.copyOf(refs);
    }
}
