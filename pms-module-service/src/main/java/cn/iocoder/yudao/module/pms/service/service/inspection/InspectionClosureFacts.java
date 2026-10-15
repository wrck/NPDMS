package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionClosureFacts {
    private final InspectionReportService reports;
    private final InspectionReportResultMapper reportResults;
    private final InspectionDetectionResultMapper results;
    private final InspectionAnnotationMapper annotations;
    private final ServiceIssueMapper issues;
    public record Query(InspectionRequestContext context, InspectionTaskDO task) { }
    public record Facts(boolean reportAvailable, boolean annotationsComplete, long openIssues) {
        public InspectionTaskStateMachine.Transition transition(InspectionTaskDO task, InspectionTaskStateMachine.Action action) {
            return new InspectionTaskStateMachine.Transition(task.getStatusCode(), action, true, reportAvailable, annotationsComplete,
                    openIssues, task.getArchiveRequestedAt() != null, task.getCustomerArchiveId() != null);
        }
    }
    public Facts current(Query query) {
        var task = query.task(); var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        // 调用方已持有任务写锁；当前读必须包含等待期间提交的新跟踪项。
        long open = issues.selectForTaskForUpdate(rows).stream().filter(i -> !"CLOSED".equals(i.getStatusCode())).count();
        if (task.getCurrentReportId() == null) return new Facts(false, false, open);
        var inputs = reportResults.selectForTask(rows).stream().filter(r -> Objects.equals(r.getReportId(), task.getCurrentReportId()))
                .map(InspectionReportResultDO::getResultId).collect(Collectors.toSet());
        var abnormal = results.selectForTask(rows).stream().filter(r -> inputs.contains(r.getId()) && "ABNORMAL".equals(r.getOutcome()))
                .map(InspectionDetectionResultDO::getId).collect(Collectors.toSet());
        var latest = latestAnnotations(new AnnotationQuery(rows, task.getCurrentReportId()));
        boolean complete = !inputs.isEmpty() && abnormal.stream().allMatch(latest::containsKey);
        return new Facts(reports.available(new InspectionReportService.ReportGate(query.context(), task, task.getCurrentReportId())), complete, open);
    }
    public record AnnotationQuery(InspectionTaskRows rows, Long reportId) { }
    public Map<Long, InspectionAnnotationDO> latestAnnotations(AnnotationQuery query) {
        Map<Long, InspectionAnnotationDO> result = new HashMap<>();
        for (var row : annotations.selectForTask(query.rows())) if (Objects.equals(row.getReportId(), query.reportId()))
            result.merge(row.getResultId(), row, (left, right) -> left.getRevisionNo() > right.getRevisionNo() ? left : right);
        return Map.copyOf(result);
    }
}
