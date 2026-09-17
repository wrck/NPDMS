package cn.iocoder.yudao.module.pms.project.api.closure;

import java.util.List;

/** PROJ 闭环就绪评估契约。实现方在调用方事务内按 root→project→task→contract→link 顺序加锁。 */
public interface ProjectClosureCheckApi {

    /** 锁定项目后执行全量闭环检查（阶段完成、门禁、任务、业务事实与子孙守卫）；结果为证据而非业务完成。 */
    ClosureEvaluation evaluateLocked(ClosureCheckCommand command);

    /** 只读运行图与任务概览（供闭环查询视图展示：终态、阶段完成、任务齐完成）。 */
    GraphOverview inspectGraph(Long tenantId, Long projectId);

    record ClosureCheckCommand(Long tenantId, Long projectId, Integer expectedProjectVersion,
                                Long treeVersion, Long actorId, String correlationId) {
    }

    record ClosureCheck(String code, boolean passed, String reason, Long targetId) {
    }

    /** passed=false 时 checks/evidence 仍是完整证据；currentStage* 供审批通过路径推进阶段使用。 */
    record ClosureEvaluation(boolean passed, String closurePolicySnapshot, List<ClosureCheck> checks,
                             String evidence, String sourceVector, String sourceDigest,
                             Long currentStageId, Integer currentStageVersion, boolean terminalStage) {
        public ClosureEvaluation {
            checks = List.copyOf(checks);
        }
    }

    record GraphOverview(boolean terminal, String completionStatus, Long currentStageId, boolean allTasksDone) {
    }
}
