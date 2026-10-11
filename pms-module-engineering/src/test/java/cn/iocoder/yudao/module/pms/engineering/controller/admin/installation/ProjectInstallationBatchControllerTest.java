package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationBatchCreateReqVO;
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
class ProjectInstallationBatchControllerTest {
    final InstallationService service = mock(InstallationService.class);
    final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    final ProjectInstallationBatchController controller = new ProjectInstallationBatchController(service, scope);
    final InstallationBatchCreateReqVO request = new InstallationBatchCreateReqVO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        request.setProjectId(9L); request.setEquipmentIds(List.of(11L, 12L));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void partialScopeCannotCreateBatch() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of(9L)));
        assertThrows(RuntimeException.class, () -> controller.create(request));
        verifyNoInteractions(service);
    }
    @Test void authorizedProjectDelegatesOneBatch() {
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(service.createInstallationBatch(request)).thenReturn(List.of(101L, 102L));
        assertEquals(List.of(101L, 102L), controller.create(request).getData());
        verify(service).createInstallationBatch(request);
    }
    @Test void emptyOrNullDeviceSetsAreInvalid() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            request.setEquipmentIds(List.of());
            assertFalse(validator.validate(request).isEmpty());
            request.setEquipmentIds(java.util.Arrays.asList(11L, null));
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
