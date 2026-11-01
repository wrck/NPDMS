package cn.iocoder.yudao.module.pms.acceptance.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionAccessGrantService;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionPublicSubmissionApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SatisfactionQuestionnairePublicControllerTest {
    private final SatisfactionAccessGrantService grants = mock(SatisfactionAccessGrantService.class);
    private final SatisfactionQuestionnairePublicController controller = new SatisfactionQuestionnairePublicController(
            grants, mock(SatisfactionPublicSubmissionApplicationService.class), mock(Environment.class));

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void unavailableGrantReturnsControlledError() {
        TenantContextHolder.setTenantId(1L);
        when(grants.inspect(1L, "invalid")).thenThrow(new IllegalStateException("SATISFACTION_GRANT_UNAVAILABLE"));
        var result = controller.inspect("invalid");
        assertEquals(403, result.getCode());
        assertEquals("链接已过期、已失效或无权访问。", result.getMsg());
    }

    @Test
    void unexpectedFailureIsNotMisreportedAsAnInvalidLink() {
        TenantContextHolder.setTenantId(1L);
        var failure = new IllegalStateException("SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
        when(grants.inspect(1L, "valid")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.inspect("valid")));
    }
}
