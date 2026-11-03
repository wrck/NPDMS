package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteInput;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationProjectSitesServiceTest {
    final InstallationMapper mapper = mock(InstallationMapper.class);
    final AssetLocationApi locations = mock(AssetLocationApi.class);
    final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final InstallationProjectSitesService service = new InstallationProjectSitesService(mapper, locations, customers, scopes);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(8L).setTenantId(1L), new MockHttpServletRequest());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()));
        when(mapper.selectCurrentProjectSiteIds(any())).thenReturn(List.of(98L, 99L));
        when(locations.getEnabledSites(any())).thenReturn(List.of(new SiteRespDTO(98L, "s", "启用", null, null, null, 0, 0L)));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void returnsOnlyEnabledCurrentSitesAndRechecksOnSubmit() {
        assertEquals(List.of(98L), service.read(20L, null).sites().stream().map(SiteRespDTO::id).toList());
        verify(mapper).selectCurrentProjectSiteIds(argThat(q -> q.tenantId().equals(1L) && q.projectId().equals(20L)));
        verify(locations).getEnabledSites(argThat(q -> q.tenantId().equals(1L) && q.siteIds().equals(List.of(98L, 99L))));
        service.validateExistingSite(20L, reference(98L, null));
        assertThrows(RuntimeException.class, () -> service.validateExistingSite(20L, reference(98L, "改名")));
        assertThrows(RuntimeException.class, () -> service.read(20L, 99L));
        assertThrows(RuntimeException.class, () -> service.validateExistingSite(20L, reference(100L, null)));
    }
    @Test void deniesProjectOutsideEditScopeBeforeReadingSites() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of(20L)));
        assertThrows(RuntimeException.class, () -> service.read(20L, null));
        verifyNoInteractions(mapper, locations, customers);
    }
    private LocationMaintenanceCommand reference(Long id, String name) {
        return new LocationMaintenanceCommand(20L, null, new SiteInput(id, null, null, name, null, null),
                null, null, null, null, null, List.of());
    }
}
