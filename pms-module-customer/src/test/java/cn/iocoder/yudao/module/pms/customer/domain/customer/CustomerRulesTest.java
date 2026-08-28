package cn.iocoder.yudao.module.pms.customer.domain.customer;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerRulesTest {

    @Test
    void temporaryCustomerRequiresReasonAndReconciliation() {
        assertThrows(IllegalArgumentException.class, () -> CustomerRules.validateTemporaryCustomer(
                CustomerSourceType.PLATFORM_TEMPORARY, null, null, null, true));
        assertThrows(IllegalArgumentException.class, () -> CustomerRules.validateTemporaryCustomer(
                CustomerSourceType.PLATFORM_TEMPORARY, null, null, "现场临时建档", false));
        assertDoesNotThrow(() -> CustomerRules.validateTemporaryCustomer(
                CustomerSourceType.PLATFORM_TEMPORARY, null, null, "现场临时建档", true));
    }

    @Test
    void temporaryCustomerRejectsCrmIdentity() {
        assertThrows(IllegalArgumentException.class, () -> CustomerRules.validateTemporaryCustomer(
                CustomerSourceType.PLATFORM_TEMPORARY, "crm-1", "v1", "现场临时建档", true));
    }

    @Test
    void deletedCustomerCanOnlyRestore() {
        assertThrows(IllegalStateException.class, () -> CustomerRules.validateTransition(
                CustomerLifecycleStatus.DELETED, CustomerLifecycleAction.DISABLE));
        assertDoesNotThrow(() -> CustomerRules.validateTransition(
                CustomerLifecycleStatus.DELETED, CustomerLifecycleAction.RESTORE));
    }
}
