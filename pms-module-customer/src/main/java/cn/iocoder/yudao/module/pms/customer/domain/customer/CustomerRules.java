package cn.iocoder.yudao.module.pms.customer.domain.customer;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;

public final class CustomerRules {

    private CustomerRules() {
    }

    public static void validateTemporaryCustomer(CustomerSourceType sourceType, String sourceKey, String sourceVersion,
                                                 String reason, boolean reconciliationPending) {
        if (sourceType != CustomerSourceType.PLATFORM_TEMPORARY) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("临时客户必须填写创建原因");
        }
        if (!reconciliationPending) {
            throw new IllegalArgumentException("临时客户必须标记为待对账");
        }
        if ((sourceKey != null && !sourceKey.isBlank()) || (sourceVersion != null && !sourceVersion.isBlank())) {
            throw new IllegalArgumentException("临时客户不能声明 CRM 来源身份");
        }
    }

    public static void validateTransition(CustomerLifecycleStatus currentStatus, CustomerLifecycleAction action) {
        if (currentStatus == CustomerLifecycleStatus.DELETED && action != CustomerLifecycleAction.RESTORE) {
            throw new IllegalStateException("已删除客户只能恢复");
        }
        if (currentStatus != CustomerLifecycleStatus.DELETED && action == CustomerLifecycleAction.RESTORE) {
            throw new IllegalStateException("只有已删除客户可以恢复");
        }
    }
}
