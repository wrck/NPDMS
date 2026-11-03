package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness.ImplementationReadinessSourceMapper;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationPlannedAddressServiceTest {
    final ImplementationReadinessSourceMapper sources = mock(ImplementationReadinessSourceMapper.class);
    final SolutionTieredReviewService reviews = mock(SolutionTieredReviewService.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final InstallationPlannedAddressService service = new InstallationPlannedAddressService(sources, reviews, permissions, scopes);
    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(8L).setTenantId(1L).setUserType(2), new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(eq(8L), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 2L, Set.of(20L), Set.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void rejectsMissingSolutionPermissionAndOutOfScopeBeforeReadingSources() {
        when(permissions.hasAnyPermissions(8L, "pms:sol-solution:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.read(20L));
        verifyNoInteractions(sources, reviews);
        when(permissions.hasAnyPermissions(8L, "pms:sol-solution:query")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 2L, Set.of(), Set.of(20L)));
        assertThrows(RuntimeException.class, () -> service.read(20L));
        verifyNoInteractions(sources, reviews);
    }
    @Test void requiresFinalMajorApprovalAndKeepsDistinctProductLocations() {
        var unapproved = new SolutionDO(); unapproved.setId(90L); unapproved.setReviewLevel(1);
        var approved = new SolutionDO(); approved.setId(89L); approved.setReviewLevel(0); approved.setVersion(4L); approved.setName("批准方案");
        String original = "{\"deploy\":[{\"productCode\":\" A \",\"location\":\"机房一\"},{\"productCode\":\"A\",\"location\":\"机房二\"},{\"deviceName\":\"A\",\"location\":\"不得按名称匹配\"}]}";
        approved.setTopology(original);
        when(sources.approvedSolutions(any())).thenReturn(List.of(unapproved, approved));
        var result = service.read(20L);
        assertEquals(89L, result.solutionId());
        assertEquals(List.of(new InstallationPlannedAddressService.Address("A", "机房一"),
                new InstallationPlannedAddressService.Address("A", "机房二"),
                new InstallationPlannedAddressService.Address("", "不得按名称匹配")), result.addresses());
        assertEquals(original, approved.getTopology());
        verify(sources).approvedSolutions(argThat(query -> query.tenantId().equals(1L) && query.projectId().equals(20L) && !query.lock()));
        verify(reviews).approved(unapproved, false);
    }
    @Test void absentApprovedPlanDoesNotInventAnAddress() {
        when(sources.approvedSolutions(any())).thenReturn(List.of());
        assertNull(service.read(20L));
    }
}
