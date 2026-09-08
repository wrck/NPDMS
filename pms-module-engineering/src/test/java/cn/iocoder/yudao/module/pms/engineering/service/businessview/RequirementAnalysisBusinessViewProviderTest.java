package cn.iocoder.yudao.module.pms.engineering.service.businessview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisBusinessViewProviderTest {

    private final PermissionApi permissions = mock(PermissionApi.class);
    private final RequirementAnalysisBusinessViewProvider provider =
            new RequirementAnalysisBusinessViewProvider(permissions);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void pageOwnerRemainsSolEvenThoughComponentLivesInProjectUi() {
        var component = provider.component();
        assertEquals("SOL", component.ownerContext());
        assertEquals("PROJ_REQUIREMENT_ANALYSIS", component.componentKey());
        assertEquals(BusinessViewComponentProvider.ViewSource.PAGE, component.viewSource());
        assertEquals("SOL_REQUIREMENT_ANALYSIS_COMMAND", component.commandProviderKey());
        assertEquals("[\"CREATE_INITIAL_DRAFT\",\"PATCH_FORM\",\"COMPLETE\",\"CREATE_DRAFT\"]",
                component.supportedActions().toString());
        verifyNoInteractions(permissions);
    }

    @Test
    void configurationPermissionIsRecheckedForEachAction() {
        TenantContextHolder.setTenantId(1L);
        var context = new BusinessViewComponentProvider.Context(1L, 9L);
        when(permissions.hasAnyPermissions(9L, "pms:project-template:publish")).thenReturn(true);
        assertTrue(provider.canConfigure(context, BusinessViewComponentProvider.ConfigurationAction.PUBLISH));
        assertFalse(provider.canConfigure(context, BusinessViewComponentProvider.ConfigurationAction.MANAGE));
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:publish");
        verify(permissions).hasAnyPermissions(9L, "pms:project-template:update");
    }

    @Test
    void spoofedTenantNeverReachesPermissionProvider() {
        TenantContextHolder.setTenantId(1L);
        assertFalse(provider.canConfigure(new BusinessViewComponentProvider.Context(2L, 9L),
                BusinessViewComponentProvider.ConfigurationAction.PUBLISH));
        verifyNoInteractions(permissions);
    }

    @Test
    void pageConfigurationDoesNotReadOrCreateAFormInstance() {
        TenantContextHolder.setTenantId(0L);
        var context = new BusinessViewComponentProvider.Context(0L, 9L);
        assertFalse(provider.validateConfiguration(context, null,
                BusinessViewComponentProvider.ValidationMode.INSPECT).dynamicFormAvailable());
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfiguration(context, 5L,
                BusinessViewComponentProvider.ValidationMode.INSPECT));
        verifyNoInteractions(permissions);
    }
}
