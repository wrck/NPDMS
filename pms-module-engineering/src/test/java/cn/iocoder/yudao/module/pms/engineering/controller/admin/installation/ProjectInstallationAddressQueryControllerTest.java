package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationCurrentAddressesReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectInstallationAddressQueryControllerTest {
    final InstallationService service = mock(InstallationService.class);
    final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    final ProjectInstallationAddressQueryController controller = new ProjectInstallationAddressQueryController(service, scope);
    final InstallationCurrentAddressesReqVO request = new InstallationCurrentAddressesReqVO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        request.setProjectId(9L); request.setEquipmentIds(List.of(11L, 12L));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void partialScopeCannotReadAddresses() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of(9L)));
        assertThrows(RuntimeException.class, () -> controller.get(request));
        verifyNoInteractions(service);
    }
    @Test void authorizedProjectDelegatesOneRead() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(service.getCurrentAddresses(9L, List.of(11L,12L))).thenReturn(List.of());
        assertTrue(controller.get(request).getData().isEmpty());
        verify(service).getCurrentAddresses(9L, List.of(11L,12L));
    }
    @Test void emptyOrNullDeviceSetsAreInvalid() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            request.setEquipmentIds(List.of());
            assertFalse(validator.validate(request).isEmpty());
            request.setEquipmentIds(java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList());
            assertFalse(validator.validate(request).isEmpty());
            request.setEquipmentIds(java.util.Arrays.asList(11L, null));
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
