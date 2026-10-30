package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectInstallationAddressControllerTest {
    final InstallationAddressCompletionApi addresses = mock(InstallationAddressCompletionApi.class);
    final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    final ProjectInstallationAddressController controller = new ProjectInstallationAddressController(addresses, scope);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void emptyScopeAndPathPlaceholdersCannotReadInstallationFacts() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of(9L)));
        assertThrows(RuntimeException.class, () -> controller.inspect(9L));
        verifyNoInteractions(addresses);
    }
    @Test void authorizedProjectUsesCurrentTenantAndReturnsOwnerCompletion() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(addresses.lockAndInspect(new InstallationAddressCompletionApi.Query(1L, 9L)))
                .thenReturn(new InstallationAddressCompletionApi.Fact(3, 2));
        var result = controller.inspect(9L).getData();
        assertEquals(3, result.get("totalDevices"));
        assertEquals(2, result.get("addressedDevices"));
        assertEquals(false, result.get("completed"));
    }
}
