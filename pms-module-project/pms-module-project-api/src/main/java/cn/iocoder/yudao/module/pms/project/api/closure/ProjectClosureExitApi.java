package cn.iocoder.yudao.module.pms.project.api.closure;

/** CLO-02 审批通过后的 PROJ 退出执行契约：复检、推进终态阶段、写入 NORMAL_CLOSED 与退出记录，整体原子。 */
public interface ProjectClosureExitApi {

    /**
     * 在调用方事务内执行：重跑闭环评估并核对评审人 → 终态阶段置 DONE →
     * 按版本关闭项目（ACTIVE→NORMAL_CLOSED）→ 插入退出记录。任一步失败抛 ServiceException。
     */
    ClosureExitResult executeApprovedExit(ClosureExitCommand command);

    record ClosureExitCommand(Long tenantId, Long projectId, Integer expectedProjectVersion,
                               Long expectedTreeVersion, Long actorId, String correlationId,
                               Long applicationId, Long snapshotId, Long expectedReviewerUserId,
                               String expectedFromStage, Long expectedServiceManagerUserId,
                               String expectedSourceDigest, String processInstanceId) {
    }

    record ClosureExitResult(Long exitRecordId, Long afterProjectVersion, Long stageInstanceId,
                             Long templateRevisionId) {
    }
}
