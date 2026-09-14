package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Monotonic coordination. No outer transaction: node commands commit/roll back independently.
 * Spring propagation semantics: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
 */
@Service
@RequiredArgsConstructor
public class ProjectRuntimeCoordinator {
    private final ProjectStageAdmissionService admission;
    private final ProjectStageCompletionService completion;
    private final ProjectRuleClosureService closure;
    private final ProjectBusinessTaskCompletionService tasks;
    private final ProjectGateRuleService gates;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper graph;
    private final cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService associations;
    private final ProjectMasterMapper projects;

    public record Result(boolean unknown, int activated, int completed) { }

    public Result reevaluate(Long projectId, Long actorId, String correlationId) {
        boolean unknown;
        int activated = 0;
        int completed = 0;
        ProjectStageCompletionService.Completion finished;
        ProjectBusinessTaskCompletionService.Result taskResults;
        int stageActivated;
        do {
            // Closure leaves optional branches pending. Their stale wakeups must not become endless UNKNOWN retries.
            var project = projectId == null ? null : projects.selectById(projectId);
            if (project == null || !TenantContextHolder.getRequiredTenantId().equals(project.getTenantId()))
                return new Result(true, activated, completed);
            if ("NORMAL_CLOSED".equals(project.getLifecycleStatus()) || "EXCEPTION_CLOSED".equals(project.getLifecycleStatus()))
                return new Result(false, activated, completed);
            if (!"ACTIVE".equals(project.getLifecycleStatus())) return new Result(true, activated, completed);
            stageActivated = 0;
            boolean admissionUnknown = false;
            var pendingStages = graph.selectStages(new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(), projectId));
            for (var stage : pendingStages) {
                if (!"PENDING".equals(stage.getStatus())) continue;
                try {
                    var admitted = admission.activateStage(projectId, actorId, correlationId, stage.getId());
                    stageActivated += (int) admitted.stream().filter(ProjectStageAdmissionService.StageAdmission::activated).count();
                    admissionUnknown |= admitted.stream().anyMatch(item -> item.outcome() == RuleEvaluation.Outcome.UNKNOWN);
                } catch (RuntimeException unavailable) {
                    // The single-stage command has rolled back before the next independent stage is evaluated.
                    admissionUnknown = true;
                }
            }
            activated += stageActivated;
            boolean gateUnknown = false;
            var gateRows = graph.selectGates(new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(), projectId));
            for (var gate : gateRows) {
                try {
                    gateUnknown |= gates.evaluate(projectId, gate.getGateCode(), actorId, correlationId).evaluation().outcome() == RuleEvaluation.Outcome.UNKNOWN;
                } catch (RuntimeException unavailable) {
                    // The proxied gate transaction has rolled back; independent nodes may still advance.
                    gateUnknown = true;
                }
            }
            taskResults = tasks.completeEligible(projectId, correlationId);
            activated += taskResults.activated();
            completed += taskResults.completed();
            int stageCompleted = 0;
            boolean stageUnknown = false;
            var stageRows = graph.selectStages(new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),projectId));
            for (var stage : stageRows) {
                if (!"ACTIVE".equals(stage.getStatus())) continue;
                try {
                    associations.synchronizeStage(projectId,stage.getId(),correlationId);
                    var result = completion.completeStage(projectId,stage.getId(),actorId,correlationId);
                    stageCompleted += result.completed();
                    stageUnknown |= result.unknown();
                } catch (RuntimeException unavailable) {
                    // The failed stage transaction has rolled back; independent stages can still finish.
                    stageUnknown = true;
                }
            }
            finished = new ProjectStageCompletionService.Completion(stageCompleted,stageUnknown);
            completed += finished.completed();
            unknown = gateUnknown || taskResults.unknown() || finished.unknown() || admissionUnknown;
        } while (finished.completed() > 0 || taskResults.completed() > 0 || taskResults.activated() > 0 || stageActivated > 0);
        var closed = closure.closeIfSatisfied(projectId, actorId, correlationId);
        // Successful formal closure ends reevaluation even if an optional, unstarted branch had unavailable facts.
        return new Result(!closed.closed() && (closed.unknown() || unknown), activated, completed);
    }
}
