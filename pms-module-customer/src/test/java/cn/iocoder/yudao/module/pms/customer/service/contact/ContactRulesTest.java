package cn.iocoder.yudao.module.pms.customer.service.contact;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactRulesTest {
    private ContactValues values(String mobile, String phone, String email) {
        return new ContactValues(" 客户测试联系人 ", "运维部", null, mobile, phone, email, null, null);
    }

    @Test void enabledContactRequiresARealContactChannelAndNormalizesBlanks() {
        assertThrows(IllegalArgumentException.class, () -> ContactRules.normalize(values(" ", null, null), 0));
        assertEquals("客户测试联系人", ContactRules.normalize(values(null, "+86 (571) 1234-5678", null), 0).name());
        assertEquals("contact@example.com", ContactRules.normalize(values(null, null, " contact@example.com "), 0).email());
        assertNull(ContactRules.normalize(values(" ", null, null), 1).mobile());
    }

    @Test void malformedValuesAreRejectedEvenWhenAnotherChannelIsValid() {
        assertThrows(IllegalArgumentException.class, () -> ContactRules.normalize(values("not-a-phone", null, "contact@example.com"), 0));
        assertThrows(IllegalArgumentException.class, () -> ContactRules.normalize(values("13800138000", null, "not-an-email"), 0));
        assertThrows(IllegalArgumentException.class, () -> ContactRules.normalize(values("(+)", null, null), 0));
        assertThrows(IllegalArgumentException.class, () -> ContactRules.normalize(values("13800138000", null, null), 2));
    }

    @Test void primaryDesignationDoesNotSilentlyReplaceAnotherContact() {
        assertDoesNotThrow(() -> ContactRules.requirePrimaryAllowed(0, true, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> ContactRules.requirePrimaryAllowed(0, true, 1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> ContactRules.requirePrimaryAllowed(1, true, null, 2L));
        assertDoesNotThrow(() -> ContactRules.requirePrimaryAllowed(0, false, 1L, 2L));
    }

    @Test void removingPrimaryRequiresAnExplicitNoPrimaryDecision() {
        assertThrows(IllegalArgumentException.class, () -> ContactRules.requireRemovalAllowed(true, false));
        assertDoesNotThrow(() -> ContactRules.requireRemovalAllowed(true, true));
        assertDoesNotThrow(() -> ContactRules.requireRemovalAllowed(false, false));
    }
}
