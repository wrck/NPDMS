package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionAnnotationService {
    private final InspectionAccessService access;
    private final InspectionTaskService tasks;
    private final InspectionClosureFacts closure;
    private final InspectionAnnotationMapper annotations;
    private final InspectionReportResultMapper reportInputs;
    private final InspectionDetectionResultMapper results;
    private final InspectionRuleQueryService rules;
    private final ServiceIssueMapper issues;
    private final ServiceIssueRemediationMapper remediations;
    private final NotifyMessageSendApi notifications;

    @Builder public record Annotate(InspectionRequestContext context, Long taskId, Long reportId, Long resultId, String kind,
                                    String reason, String changeReason, Long responsibleUserId, LocalDateTime dueAt) { }
    public record Complete(InspectionRequestContext context, Long taskId) { }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary annotate(Annotate command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:annotate", true, false));
        require(Set.of("ANNOTATION_PENDING", "TRACKING", "CLOSED").contains(task.getStatusCode()), "当前阶段不能标注问题");
        require(Objects.equals(task.getCurrentReportId(), command.reportId()), "报告版本已变化，请刷新");
        require(command.kind() != null && Set.of("FALSE_POSITIVE", "IGNORE", "TRACK").contains(command.kind()), "请选择有效标注类型");
        InspectionTaskService.validateText(command.reason(), 2000, "标注原因或依据");
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        require(reportInputs.selectForTask(rows).stream().anyMatch(r -> Objects.equals(r.getReportId(), command.reportId())
                && Objects.equals(r.getResultId(), command.resultId())), "检测项不属于当前报告");
        var result = results.selectById(command.resultId());
        require(result != null && Objects.equals(result.getTenantId(), task.getTenantId()) && Objects.equals(result.getTaskId(), task.getId())
                && "ABNORMAL".equals(result.getOutcome()), "只能对实际检出异常进行问题标注");
        var previous = closure.latestAnnotations(new InspectionClosureFacts.AnnotationQuery(rows, command.reportId())).get(command.resultId());
        if (previous != null) InspectionTaskService.validateText(command.changeReason(), 2000, "标注修改原因");
        var issue = issues.selectForTask(rows).stream().filter(i -> Objects.equals(i.getReportId(), command.reportId())
                && Objects.equals(i.getResultId(), command.resultId())).findFirst().orElse(null);
        if ("TRACK".equals(command.kind())) {
            require(command.dueAt() != null, "确认跟踪须填写计划解决时间");
            access.responsible(new InspectionAccessService.Subject(command.context(), task.getProjectId(), command.responsibleUserId(), "pms:service-issue:remediate"));
            require(issue == null || !"CLOSED".equals(issue.getStatusCode()) || Objects.equals(issue.getResponsibleUserId(), command.responsibleUserId())
                    && Objects.equals(issue.getDueAt(), command.dueAt()), "已闭环待办的责任人和期限保留历史，不能重新分配");
        }
        var annotation = new InspectionAnnotationDO(); annotation.setTenantId(task.getTenantId()); annotation.setVersion(1L);
        annotation.setTaskId(task.getId()); annotation.setReportId(command.reportId()); annotation.setResultId(command.resultId());
        annotation.setRevisionNo(previous == null ? 1 : previous.getRevisionNo() + 1); annotation.setKind(command.kind());
        annotation.setReason(command.reason().trim()); annotation.setChangeReason(command.changeReason());
        annotation.setResponsibleUserId("TRACK".equals(command.kind()) ? command.responsibleUserId() : null);
        annotation.setDueAt("TRACK".equals(command.kind()) ? command.dueAt() : null);
        annotation.setAnnotatedBy(command.context().actorId()); annotation.setAnnotatedAt(LocalDateTime.now()); annotations.insert(annotation);
        if ("TRACK".equals(command.kind())) {
            boolean notify = issue == null || !Objects.equals(issue.getResponsibleUserId(), command.responsibleUserId());
            if (issue == null) {
                issue = new ServiceIssueDO(); issue.setTenantId(task.getTenantId()); issue.setVersion(1L); issue.setTaskId(task.getId());
                issue.setReportId(command.reportId()); issue.setResultId(command.resultId()); issue.setAnnotationId(annotation.getId());
                issue.setResponsibleUserId(command.responsibleUserId()); issue.setDueAt(command.dueAt());
                issue.setDescription(command.reason()); issue.setSeverityCode(rules.detail(result.getRuleRevisionId()).getSeverityCode());
                issue.setStatusCode("PENDING"); issues.insert(issue);
            } else if (!"CLOSED".equals(issue.getStatusCode())) {
                boolean reassigned = !Objects.equals(issue.getResponsibleUserId(), command.responsibleUserId()) || !Objects.equals(issue.getDueAt(), command.dueAt());
                issue.setResponsibleUserId(command.responsibleUserId()); issue.setDueAt(command.dueAt()); issue.setAnnotationId(annotation.getId());
                issues.updateById(issue);
                if (reassigned) {
                    var event = new ServiceIssueRemediationDO(); event.setTenantId(task.getTenantId()); event.setVersion(1L);
                    event.setTaskId(task.getId()); event.setIssueId(issue.getId()); event.setActionCode("REASSIGN");
                    event.setContent("标注修订重新分配责任人/期限：" + command.changeReason()); event.setActorId(command.context().actorId());
                    event.setActionAt(LocalDateTime.now()); remediations.insert(event);
                }
            }
            if (notify) {
                var message = new NotifySendSingleToUserReqDTO().setUserId(issue.getResponsibleUserId())
                        .setTemplateCode("pms_inspection_issue_assigned").setDeliveryKey("ins-issue-" + issue.getId() + "-annotation-" + annotation.getId())
                        .setTemplateParams(Map.of("taskCode", task.getTaskCode(), "issueId", issue.getId().toString(),
                                "dueAt", issue.getDueAt().toString(), "url", "/pms/inspection/tasks/" + task.getId() + "?issueId=" + issue.getId()));
                notifications.sendSingleMessageToAdmin(message);
            }
        }
        task.setArchiveRequestedAt(null); task.setArchiveRequestedBy(null);
        if ("ANNOTATION_PENDING".equals(task.getStatusCode())) tasks.save(new InspectionTaskService.Save(command.context(), task,
                "ANNOTATE_RESULT", task.getStatusCode(), "保存问题标注版本" + annotation.getRevisionNo()));
        else {
            var facts = closure.current(new InspectionClosureFacts.Query(command.context(), task));
            tasks.transition(new InspectionTaskService.Change(command.context(), task,
                    facts.transition(task, InspectionTaskStateMachine.Action.UPDATE_ANNOTATIONS), "修订问题标注并重验闭环条件"));
        }
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary complete(Complete command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:annotate", true, false));
        var facts = closure.current(new InspectionClosureFacts.Query(command.context(), task));
        tasks.transition(new InspectionTaskService.Change(command.context(), task,
                facts.transition(task, InspectionTaskStateMachine.Action.ANNOTATIONS_COMPLETE), "确认完成全部问题标注"));
        return InspectionTaskService.summary(task);
    }
}
