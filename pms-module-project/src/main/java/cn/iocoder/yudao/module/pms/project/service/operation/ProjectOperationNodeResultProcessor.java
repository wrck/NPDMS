package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

/** One recipient transaction; authoritative Owner facts, not an old message, decide whether completion is true. */
@Service
@RequiredArgsConstructor
public class ProjectOperationNodeResultProcessor {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper nodes;
    private final ProjectTaskAdmissionService taskAdmission;
    private final ProjectStageAdmissionService stageAdmission;
    private final ProjectTaskBusinessAssociationService associations;
    private final ProjectTaskLifecycleService tasks;
    private final ProjectStageCompletionService stages;
    private final PlatformBusinessEventApi outbox;
    private final OperationAuditApi audit;

    @Transactional(rollbackFor = Exception.class)
    public String process(ProjectResultTargetEvent target) {
        var source = target.source();
        source.requireEnvelope(source.eventId(),TenantContextHolder.getRequiredTenantId());
        var project = projects.selectByIdForUpdate(source.projectId());
        if (project == null || !source.tenantId().equals(project.getTenantId())) throw new IllegalArgumentException("RESULT_PROJECT_INVALID");
        if (!"ACTIVE".equals(project.getLifecycleStatus())) return "PROJECT_NOT_ACTIVE";
        var scope = new ProjectPlanScopeQuery(source.tenantId(),source.projectId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(),target.planVersionId()) || !Objects.equals(project.getActivePlanVersionId(),plan.getId()))
            return "OBSOLETE_RECIPIENT";
        var matches = nodes.selectCurrentForUpdate(scope).stream().filter(r -> Objects.equals(r.getId(),target.executionId())
                && Objects.equals(r.getNodeInstanceId(),target.nodeId()) && Objects.equals(r.getNodeKind(),target.nodeKind())
                && Objects.equals(r.getNodeKey(),target.nodeKey()) && Objects.equals(r.getContractId(),target.contractId())
                && Objects.equals(r.getPlanVersionId(),target.planVersionId())).toList();
        if (matches.size() != 1) return "OBSOLETE_RECIPIENT";
        boolean changed = false;
        if ("TASK".equals(target.nodeKind())) {
            var admitted = taskAdmission.activateEligible(source.projectId(),target.nodeId(),source.correlationId());
            if (admitted.unknown()) throw new IllegalStateException("RESULT_ADMISSION_UNKNOWN");
            if (!admitted.activated() && !"ACTIVE".equals(matches.getFirst().getStatus())) return "WAITING_ADMISSION_OR_TERMINAL";
            associations.synchronize(source.projectId(),target.nodeId(),source.correlationId());
            var completed = tasks.completeFromBusinessResult(source.projectId(),target.nodeId(),source.correlationId());
            if (completed.unknown()) throw new IllegalStateException("RESULT_COMPLETION_UNKNOWN");
            changed = admitted.activated() || completed.completed();
        } else {
            var admitted = stageAdmission.activateStage(source.projectId(),source.actorId(),source.correlationId(),target.nodeId());
            if (admitted.stream().anyMatch(r -> r.outcome() == RuleEvaluation.Outcome.UNKNOWN)) throw new IllegalStateException("RESULT_ADMISSION_UNKNOWN");
            if (admitted.stream().noneMatch(ProjectStageAdmissionService.StageAdmission::activated)
                    && !"ACTIVE".equals(matches.getFirst().getStatus())) return "WAITING_ADMISSION_OR_TERMINAL";
            associations.synchronizeStage(source.projectId(),target.nodeId(),source.correlationId());
            var completed = stages.completeStage(source.projectId(),target.nodeId(),source.actorId(),source.correlationId());
            if (completed.unknown()) throw new IllegalStateException("RESULT_COMPLETION_UNKNOWN");
            changed = admitted.stream().anyMatch(ProjectStageAdmissionService.StageAdmission::activated) || completed.completed() > 0;
        }
        String outcome = changed ? "ADVANCED" : "REEVALUATED_WAITING_OR_TERMINAL";
        audit.record(source.tenantId(),source.actorId(),source.correlationId(),"PROJECT_RESULT_REEVALUATED","ProjectNode",
                target.nodeId().toString(),"SUCCESS",Map.of("eventId",source.eventId(),"targetEventId",target.eventId(),
                        "executionId",target.executionId(),"planVersionId",target.planVersionId(),"sourceFactVersion",source.businessFactVersion(),
                        "outcome",outcome,"evidenceAuthority","OWNER_FACTS_AND_FORMAL_COMPLETION_EVALUATION"));
        if (changed) {
            var event = ProjectRuleReevaluationRequested.create(source.tenantId(),source.projectId(),source.actorId(),source.correlationId());
            outbox.append("Project",source.projectId().toString(),new BusinessEvent(event.eventId(),ProjectRuleReevaluationRequested.EVENT_TYPE,JsonUtils.toJsonString(event)));
        }
        return outcome;
    }
}
