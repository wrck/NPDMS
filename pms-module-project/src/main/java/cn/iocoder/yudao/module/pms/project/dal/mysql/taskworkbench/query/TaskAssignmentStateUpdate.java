package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

public record TaskAssignmentStateUpdate(Long tenantId, Long projectId, Long projectTaskId,
                                        int expectedVersion, String expectedStatus,
                                        String nextStatus, String updater) {
}
