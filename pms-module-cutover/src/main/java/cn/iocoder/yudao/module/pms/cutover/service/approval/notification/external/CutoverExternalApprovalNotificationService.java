package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.time.LocalDateTime;
import java.util.Objects;

public class CutoverExternalApprovalNotificationService {

    private static final int MAX_BATCH_SIZE = 100;

    private final CutoverExternalApprovalNotificationTransactionExecutor executor;

    public CutoverExternalApprovalNotificationService(
            CutoverExternalApprovalNotificationTransactionExecutor executor) {
        this.executor = executor;
    }

    public DeliveryResult deliverDue(long tenantId, LocalDateTime dueAt, int batchSize) {
        require(tenantId > 0 && dueAt != null && batchSize > 0 && batchSize <= MAX_BATCH_SIZE,
                "外部提醒领取参数无效");
        require(Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId), "外部提醒租户上下文不一致");
        return executor.deliverBatch(tenantId, dueAt, batchSize);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record DeliveryResult(int accepted, int deliveryUnknown, int retryScheduled) {
        public DeliveryResult {
            if (accepted < 0 || deliveryUnknown < 0 || retryScheduled < 0) {
                throw new IllegalArgumentException("外部提醒投递计数无效");
            }
        }
    }
}
