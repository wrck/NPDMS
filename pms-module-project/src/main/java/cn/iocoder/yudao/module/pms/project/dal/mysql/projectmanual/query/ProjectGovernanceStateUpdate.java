package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 项目异常治理三状态轴乐观锁更新参数。 */
public record ProjectGovernanceStateUpdate(
        Long tenantId,
        Long projectId,
        Integer expectedVersion,
        String expectedLifecycleStatus,
        String currentStage,
        String lifecycleStatus,
        String assignmentStatus,
        String updater) {
}
