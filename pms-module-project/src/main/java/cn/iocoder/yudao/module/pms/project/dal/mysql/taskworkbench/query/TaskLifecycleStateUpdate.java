package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;

/** TASK_NATIVE动作的状态、实际时间和进度CAS更新。 */
public record TaskLifecycleStateUpdate(Long tenantId, Long projectId, Long projectTaskId,
                                       int expectedVersion, String expectedStatus, String nextStatus,
                                       boolean initializeActualStartTime, boolean setActualEndTime,
                                       Integer progress, LocalDateTime occurredAt, String updater) {
}
