package cn.iocoder.yudao.module.pms.project.service.taskworkbench.event;

import java.time.LocalDateTime;

/** TaskCompleted本地投递消息及其Outbox冻结载荷。 */
public record TaskCompletedMessage(String eventId, Long tenantId, Long projectId, Long projectTaskId,
                                   Long completionEvaluationId, int taskVersion,
                                   Long executionContractId, int contractVersion, Long factVersion,
                                   Long completedBy, LocalDateTime occurredAt) {

    public record Payload(Long tenantId, Long projectId, Long projectTaskId, Long completionEvaluationId,
                          int taskVersion, Long executionContractId, int contractVersion, Long factVersion,
                          Long completedBy, LocalDateTime occurredAt) {
    }
}
