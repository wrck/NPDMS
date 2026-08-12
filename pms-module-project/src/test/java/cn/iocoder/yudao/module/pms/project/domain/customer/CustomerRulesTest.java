package cn.iocoder.yudao.module.pms.project.domain.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerRulesTest {

    @Test
    void shouldRejectChangingCustomerCode() {
        assertThrows(IllegalArgumentException.class,
                () -> CustomerRules.requireUnchangedCode("CUSTOMER-001", "CUSTOMER-002"));
    }

    @Test
    void shouldAllowKeepingCustomerCode() {
        assertDoesNotThrow(() -> CustomerRules.requireUnchangedCode("CUSTOMER-001", "CUSTOMER-001"));
    }

    @Test
    void shouldRejectSecondActivePrimaryContact() {
        assertThrows(IllegalStateException.class,
                () -> CustomerRules.requirePrimaryContactAvailable(true, true));
    }

    @Test
    void shouldAllowPrimaryContactWhenNoneIsActive() {
        assertDoesNotThrow(() -> CustomerRules.requirePrimaryContactAvailable(true, false));
    }

}
