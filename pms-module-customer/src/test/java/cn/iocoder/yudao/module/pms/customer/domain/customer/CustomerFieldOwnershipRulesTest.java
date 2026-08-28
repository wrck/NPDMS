package cn.iocoder.yudao.module.pms.customer.domain.customer;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerFieldOwnershipRulesTest {

    @Test
    void businessUpdateRejectsCrmFieldsWhenMapped() {
        assertThrows(IllegalArgumentException.class, () ->
                CustomerFieldOwnershipRules.validateBusinessUpdate(Set.of("name"), true));
        assertDoesNotThrow(() ->
                CustomerFieldOwnershipRules.validateBusinessUpdate(Set.of("remark"), true));
    }

    @Test
    void crmUpdateRejectsPlatformFields() {
        assertThrows(IllegalArgumentException.class, () ->
                CustomerFieldOwnershipRules.validateCrmUpdate(Set.of("remark")));
        assertDoesNotThrow(() ->
                CustomerFieldOwnershipRules.validateCrmUpdate(Set.of("name", "shortName")));
    }
}
