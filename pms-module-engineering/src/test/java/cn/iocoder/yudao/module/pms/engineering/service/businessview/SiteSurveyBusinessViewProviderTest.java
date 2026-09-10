package cn.iocoder.yudao.module.pms.engineering.service.businessview;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyBusinessViewProviderTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final SiteSurveyBusinessViewProvider provider = new SiteSurveyBusinessViewProvider(permissions);
    private final BusinessViewComponentProvider.Context context = new BusinessViewComponentProvider.Context(3L, 9L);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(3L);
        LoginUser login = new LoginUser();
        login.setId(9L);
        login.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCatalogIsIndependentTypedSurveyPageWithProjectContext() {
        var component = provider.component();
        assertEquals("SITE_SURVEY", component.entityType());
        assertEquals("SOL", component.ownerContext());
        assertEquals("SOL_SITE_SURVEY", component.componentKey());
        assertEquals("1", component.componentVersion());
        assertEquals(BusinessViewComponentProvider.ViewSource.PAGE, component.viewSource());
        assertEquals("[\"projectId\"]", component.contextSchema().get("required").toString());
        assertTrue(component.supportedActions().toString().contains("CONFIRM"));
        assertFalse(component.supportedActions().toString().contains("READY"));
        assertEquals("SOL_SITE_SURVEY_COMMAND", component.commandProviderKey());
        verifyNoInteractions(permissions);
    }

    @Test
    void configurationChecksRealTemplatePermissionsNeverImplicitlyGrants() {
        for (var action : BusinessViewComponentProvider.ConfigurationAction.values()) {
            assertFalse(provider.canConfigure(context, action));
        }
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:query");
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:create", "pms:project-template:update");
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:publish");
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:disable");
        when(permissions.hasAnyPermissions(9L, "pms:project-template:publish")).thenReturn(true);
        assertTrue(provider.canConfigure(context, BusinessViewComponentProvider.ConfigurationAction.PUBLISH));
        assertFalse(provider.canConfigure(context, BusinessViewComponentProvider.ConfigurationAction.MANAGE));
    }

    @Test
    void forgedTenantOrActorFailsWithoutPermissionLookup() {
        assertFalse(provider.canConfigure(new BusinessViewComponentProvider.Context(4L, 9L),
                BusinessViewComponentProvider.ConfigurationAction.QUERY));
        assertFalse(provider.canConfigure(new BusinessViewComponentProvider.Context(3L, 8L),
                BusinessViewComponentProvider.ConfigurationAction.QUERY));
        verifyNoInteractions(permissions);
    }

    @Test
    void pageConfigurationNeverReadsCreatesOrClaimsDynamicFormAvailability() {
        for (var mode : BusinessViewComponentProvider.ValidationMode.values()) {
            assertFalse(provider.validateConfiguration(context, null, mode).dynamicFormAvailable());
            assertThrows(IllegalArgumentException.class, () -> provider.validateConfiguration(context, 1L, mode));
        }
        verifyNoInteractions(permissions);
    }
}
