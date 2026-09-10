package cn.iocoder.yudao.module.pms.project.service.businessview;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptanceBusinessViewProviderTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AcceptanceBusinessViewProvider provider = new AcceptanceBusinessViewProvider(permissions);
    private final Context context = new Context(3L, 9L);

    @BeforeEach
    void login() {
        TenantContextHolder.setTenantId(3L);
        LoginUser login = new LoginUser(); login.setId(9L); login.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
    }

    @AfterEach
    void clean() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void registersOnlyExistingReportPageAndNoIndependentCreateAction() {
        var component = provider.component();
        assertEquals("ACCEPTANCE", component.entityType()); assertEquals("ACC", component.ownerContext());
        assertEquals("ACC_ACCEPTANCE_REPORT", component.componentKey()); assertEquals(ViewSource.PAGE, component.viewSource());
        assertEquals("[\"QUERY\",\"MANAGE\",\"UPDATE\",\"REVOKE\",\"LINK\",\"UNLINK\"]", component.supportedActions().toString());
        assertEquals("projectId", component.contextSchema().get("required").get(0).asString());
        assertFalse(provider.validateConfiguration(context, null, ValidationMode.INSPECT).dynamicFormAvailable());
        assertFalse(provider.validateConfiguration(context, null, ValidationMode.LOCK_FOR_PUBLISH).dynamicFormAvailable());
        verifyNoInteractions(permissions);
    }

    @Test
    void configurationUsesTemplatePermissionsNotReportWritePermission() {
        when(permissions.hasAnyPermissions(9L, "pms:acceptance:report:write")).thenReturn(true);
        for (var action : ConfigurationAction.values()) assertFalse(provider.canConfigure(context, action));
        when(permissions.hasAnyPermissions(9L, "pms:project-template:query")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:project-template:create", "pms:project-template:update")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:project-template:publish")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:project-template:disable")).thenReturn(true);
        for (var action : ConfigurationAction.values()) assertTrue(provider.canConfigure(context, action));
    }

    @Test
    void forgedContextAndDynamicFormRevisionFailClosed() {
        assertFalse(provider.canConfigure(new Context(4L, 9L), ConfigurationAction.MANAGE));
        assertFalse(provider.canConfigure(new Context(3L, 8L), ConfigurationAction.QUERY));
        assertFalse(provider.canConfigure(context, null));
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfiguration(new Context(4L, 9L), null, ValidationMode.INSPECT));
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfiguration(context, 42L, ValidationMode.INSPECT));
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfiguration(context, null, null));
        verifyNoInteractions(permissions);
    }
}
