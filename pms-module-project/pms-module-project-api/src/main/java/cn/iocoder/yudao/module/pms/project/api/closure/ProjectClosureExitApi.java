package cn.iocoder.yudao.module.pms.project.api.closure;

/** CLO-02 审批通过后的 PROJ 退出执行契约：复检、推进终态阶段、写入 NORMAL_CLOSED 与退出记录，整体原子。 */
public interface ProjectClosureExitApi {

    /**
     * 在调用方事务内执行：重跑闭环评估并核对评审人 → 终态阶段置 DONE →
     * 按版本关闭项目（ACTIVE→NORMAL_CLOSED）→ 插入退出记录。任一步失败抛 ServiceException。
     * actorId 保留申请人身份以复验项目管理授权；当前认证用户必须为同租户的实际材料审核人。
     * 业务成果按当前审核人的既有读取权限复验，流程待办本身不授予业务数据权限。
     */
    ClosureExitResult executeApprovedExit(ClosureExitCommand command);

    record ClosureExitCommand(Long tenantId, Long projectId, Integer expectedProjectVersion,
                               Long expectedTreeVersion, Long actorId, String correlationId,
                               Long applicationId, Long snapshotId, Long expectedReviewerUserId,
                               String expectedFromStage, Long expectedServiceManagerUserId,
                               String expectedSourceDigest, String processInstanceId,
                               Integer sourceRecordRevision) {
    }

    record ClosureExitResult(Long exitRecordId, Long afterProjectVersion, Long stageInstanceId,
                             Long templateRevisionId) {
    }
}
