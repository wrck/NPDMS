package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectOrganizationScopeServiceTest {
    final OrganizationScopeApi organizations = mock(OrganizationScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final AdminUserApi users = mock(AdminUserApi.class);
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectOrganizationScopeService service = new ProjectOrganizationScopeService(organizations, permissions, users, projects);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    ProjectOrganizationScopeService.Query query(boolean summary) { return new ProjectOrganizationScopeService.Query(1L, 9L, "PROJECT_VIEW", summary, null); }
    void manager() {
        when(permissions.hasAnyRoles(eq(9L), any(String[].class))).thenReturn(true);
        var user = new AdminUserRespDTO(); user.setId(9L); user.setStatus(0); when(users.getUser(9L)).thenReturn(user);
    }
    UserCompanyDepartmentScopeRespDTO row(long company, Long department, String role) {
        var row = new UserCompanyDepartmentScopeRespDTO(); row.setUserId(9L); row.setCompanyId(company);
        row.setDepartmentId(department); row.setScopeRole(role); return row;
    }
    @Test void serviceManagerUsesOnlyServiceRoleRowsAndPreservesCompanyDepartmentPairs() {
        manager();
        when(organizations.getActiveScopes(9L)).thenReturn(List.of(row(11, 21L, "SERVICE_MANAGER"),
                row(12, 22L, "SERVICE_MANAGER_L1"), row(13, 23L, "PROJECT_MANAGER"), row(14, null, "SERVICE_MANAGER")));
        assertEquals(List.of(new Organization(11L,21L), new Organization(12L,22L),new Organization(14L,null)), service.rows(query(false)));
        assertEquals(List.of(new Organization(13L,23L)), service.rows(query(true)));
    }
    @Test void globalManagerRoleWithoutEffectiveOrganizationRowsGrantsNothing() {
        manager(); when(organizations.getActiveScopes(9L)).thenReturn(List.of());
        assertEquals(Set.of(), service.projectIds(query(false))); verifyNoInteractions(projects);
    }
    @Test void ordinaryUserCannotTurnOrganizationRowsIntoManagementOrOverview() {
        assertEquals(Set.of(), service.projectIds(query(false))); assertEquals(Set.of(), service.projectIds(query(true)));
        verifyNoInteractions(users, organizations, projects);
    }
    @Test void disabledOrMissingUserAndForeignTenantFailClosed() {
        manager(); users.getUser(9L).setStatus(1);
        assertEquals(Set.of(),service.projectIds(query(false))); verifyNoInteractions(organizations, projects);
        assertThrows(IllegalArgumentException.class, () -> service.rows(new ProjectOrganizationScopeService.Query(2L,9L,"PROJECT_VIEW",false,null)));
    }
    @Test void unavailableSystemProviderDoesNotFallBackToWholeCompany() {
        manager(); when(organizations.getActiveScopes(9L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.projectIds(query(false))); verifyNoInteractions(projects);
    }
    @Test void duplicateScopesDeduplicateAndCandidateSetCannotExpand() {
        manager(); when(organizations.getActiveScopes(9L)).thenReturn(List.of(row(11,21L,"SERVICE_MANAGER"),row(11,21L,"SERVICE_MANAGER")));
        when(projects.selectOrganizationProjectIds(any())).thenReturn(List.of(101L));
        assertEquals(Set.of(101L),service.projectIds(new ProjectOrganizationScopeService.Query(1L,9L,"PROJECT_MANAGE",false,Set.of(101L))));
        verify(projects).selectOrganizationProjectIds(argThat(q -> q.organizations().equals(List.of(new Organization(11L,21L)))
                && q.candidateProjectIds().equals(Set.of(101L)) && q.activeOnly()));
        clearInvocations(projects);
        assertEquals(Set.of(),service.projectIds(new ProjectOrganizationScopeService.Query(1L,9L,"PROJECT_MANAGE",false,Set.of()))); verifyNoInteractions(projects);
    }
}
