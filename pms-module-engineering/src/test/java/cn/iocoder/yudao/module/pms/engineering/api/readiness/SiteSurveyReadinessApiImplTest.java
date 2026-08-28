package cn.iocoder.yudao.module.pms.engineering.api.readiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SiteSurveyReadinessApiImplTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void inspectUsesTrustedTenantAndActor() {
        PreparationReadinessService service = mock(PreparationReadinessService.class);
        SiteSurveyReadinessApiImpl api = new SiteSurveyReadinessApiImpl(service);
        SiteSurveyReadinessQuery query = new SiteSurveyReadinessQuery(100L, 200L);
        TenantContextHolder.setTenantId(3L);
        login(9L);

        api.inspect(query);

        verify(service).inspect(query, 3L, 9L);
    }

    @Test
    void inspectWithoutTrustedTenantFailsClosed() {
        PreparationReadinessService service = mock(PreparationReadinessService.class);
        SiteSurveyReadinessApiImpl api = new SiteSurveyReadinessApiImpl(service);
        login(9L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> api.inspect(new SiteSurveyReadinessQuery(100L, 200L)));

        assertEquals(PREPARATION_PROJECT_FACT_INVALID.getCode(), error.getCode());
        verifyNoInteractions(service);
    }

    private void login(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }
}
