package cn.iocoder.yudao.module.pms.project.service.taskworkbench.event;

import java.time.LocalDateTime;
import java.util.Map;

/** TaskCompleted本地投递消息及其Outbox冻结载荷。PM-11业务事实使用Owner不透明版本。 */
public record TaskCompletedMessage(String eventId, Long tenantId, Long projectId, Long projectTaskId,
                                   Long completionEvaluationId, int taskVersion,
                                   Long executionContractId, int contractVersion, Long factVersion,
                                   Long completedBy, LocalDateTime occurredAt, Map<String, Object> businessFacts) {
    public TaskCompletedMessage(String eventId, Long tenantId, Long projectId, Long projectTaskId,
                                Long completionEvaluationId, int taskVersion, Long executionContractId,
                                int contractVersion, Long factVersion, Long completedBy, LocalDateTime occurredAt) {
        this(eventId, tenantId, projectId, projectTaskId, completionEvaluationId, taskVersion,
                executionContractId, contractVersion, factVersion, completedBy, occurredAt, null);
    }

    public record Payload(Long tenantId, Long projectId, Long projectTaskId, Long completionEvaluationId,
                          int taskVersion, Long executionContractId, int contractVersion, Long factVersion,
                          Long completedBy, LocalDateTime occurredAt, Map<String, Object> businessFacts) {
        public Payload(Long tenantId, Long projectId, Long projectTaskId, Long completionEvaluationId,
                       int taskVersion, Long executionContractId, int contractVersion, Long factVersion,
                       Long completedBy, LocalDateTime occurredAt) {
            this(tenantId, projectId, projectTaskId, completionEvaluationId, taskVersion, executionContractId,
                    contractVersion, factVersion, completedBy, occurredAt, null);
        }
    }
}
