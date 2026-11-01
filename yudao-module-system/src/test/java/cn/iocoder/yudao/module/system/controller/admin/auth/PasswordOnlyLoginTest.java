package cn.iocoder.yudao.module.system.controller.admin.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordOnlyLoginTest {
    private final AuthController controller = new AuthController();

    @Test
    void alternativeLoginAndRegistrationCannotIssueTokens() {
        assertEquals(403, controller.smsLogin(null).getCode());
        assertEquals(403, controller.socialQuickLogin(null).getCode());
        assertEquals(403, controller.socialLogin(1, "https://example.test").getCode());
        assertEquals(403, controller.register(null).getCode());
    }
}
