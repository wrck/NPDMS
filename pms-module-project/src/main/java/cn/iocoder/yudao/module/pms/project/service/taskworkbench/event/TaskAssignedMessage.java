package cn.iocoder.yudao.module.pms.project.service.taskworkbench.event;

import java.time.LocalDateTime;

/** TaskAssigned本地投递消息及其Outbox冻结载荷。 */
public record TaskAssignedMessage(String eventId, Long tenantId, Long projectId, Long projectTaskId,
                                  Long assigneeUserId, Long assignmentId, int taskVersion,
                                  Long assignedBy, LocalDateTime occurredAt) {

    public record Payload(Long tenantId, Long projectId, Long projectTaskId, Long assigneeUserId,
                          Long assignmentId, int taskVersion, Long assignedBy,
                          LocalDateTime occurredAt) {
    }
}
