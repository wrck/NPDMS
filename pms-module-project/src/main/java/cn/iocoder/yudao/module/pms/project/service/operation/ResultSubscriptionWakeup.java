package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import java.util.Objects;
import java.util.UUID;

/** An exact durable recipient, not a request to select a newer plan or execution round. */
public record ResultSubscriptionWakeup(String eventId, int eventVersion, Long tenantId, Long projectId,
                                       Long subscriptionId, Long planVersionId, Long executionId, Long contractId) {
    public static final String EVENT_TYPE = "PMS.ResultSubscriptionWakeup.v1";
    public ResultSubscriptionWakeup {
        UUID.fromString(eventId);
        if (eventVersion != 1 || tenantId == null || tenantId < 0 || !positive(projectId) || !positive(subscriptionId)
                || !positive(planVersionId) || !positive(executionId) || !positive(contractId))
            throw new IllegalArgumentException("SUBSCRIPTION_WAKEUP_INVALID");
    }
    public static ResultSubscriptionWakeup create(ResultSubscriptionDO row) {
        return new ResultSubscriptionWakeup(UUID.randomUUID().toString(), 1, row.getTenantId(), row.getProjectId(),
                row.getId(), row.getPlanVersionId(), row.getExecutionId(), row.getContractId());
    }
    public boolean matches(ResultSubscriptionDO row) {
        return row != null && Objects.equals(tenantId,row.getTenantId()) && Objects.equals(projectId,row.getProjectId())
                && Objects.equals(subscriptionId,row.getId()) && Objects.equals(planVersionId,row.getPlanVersionId())
                && Objects.equals(executionId,row.getExecutionId()) && Objects.equals(contractId,row.getContractId());
    }
    private static boolean positive(Long id) { return id != null && id > 0; }
}
