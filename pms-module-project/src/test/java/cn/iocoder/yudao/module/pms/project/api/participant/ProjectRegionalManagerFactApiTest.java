package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectRegionalManagerFactApiTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final OrganizationScopeApi organizations = mock(OrganizationScopeApi.class);
    private final ProjectRegionalManagerFactApi api = new ProjectRegionalManagerFactApiImpl(projects, organizations);
    private final ProjectRegionalManagerFactApi.Query query = new ProjectRegionalManagerFactApi.Query(1L, 20L, 9L);
    private final ProjectMasterDO project = new ProjectMasterDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setTenantId(1L), new MockHttpServletRequest());
        project.setId(20L); project.setTenantId(1L); project.setVersion(3L); project.setLifecycleStatus("ACTIVE");
        project.setCompanyId(11L); project.setDepartmentId(21L);
        when(projects.selectById(20L)).thenReturn(project);
        when(projects.selectByIdForUpdate(20L)).thenReturn(project);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void qualificationUsesExactProjectOrganizationAndRealSystemServiceRoleSource() {
        when(organizations.inspectRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
        assertEquals(new ProjectRegionalManagerFactApi.Fact(20L, 9L, 3L), api.inspect(query));
        verify(organizations).inspectRoleScope(argThat(q -> q.companyId().equals(11L) && q.departmentId().equals(21L)
                && q.userId().equals(9L) && q.tenantId().equals(1L) && q.requiredRoleCodes().equals(ProjectMemberRoles.SERVICE_CODES)));
        when(organizations.inspectRoleScope(any())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> api.inspect(query));
    }
    @Test void sourceRevokedAfterPrecheckCannotAuthorizeLockedWrite() {
        when(organizations.inspectRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
        var before = api.inspect(query);
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(
                new ProjectRegionalManagerFactApi.RevalidationQuery(query, before.projectVersion())));
        verify(organizations).lockRoleScope(any());
        when(organizations.lockRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
        assertEquals(before, api.lockAndRevalidate(new ProjectRegionalManagerFactApi.RevalidationQuery(query, 3L)));
        var order = inOrder(projects, organizations);
        order.verify(projects).selectByIdForUpdate(20L); order.verify(organizations).lockRoleScope(any());
    }
    @Test void staleProjectVersionFailsBeforeAcquiringUnrelatedOrganizationSource() {
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(
                new ProjectRegionalManagerFactApi.RevalidationQuery(query, 2L)));
        verifyNoInteractions(organizations);
    }
    @Test void actorTenantClosedAndMissingOrganizationFailClosed() {
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectRegionalManagerFactApi.Query(2L, 20L, 9L)));
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectRegionalManagerFactApi.Query(1L, 20L, 10L)));
        project.setLifecycleStatus("NORMAL_CLOSED"); assertThrows(RuntimeException.class, () -> api.inspect(query));
        project.setLifecycleStatus("ACTIVE"); project.setCompanyId(null); assertThrows(RuntimeException.class, () -> api.inspect(query));
        project.setCompanyId(11L); project.setDepartmentId(0L); assertThrows(RuntimeException.class, () -> api.inspect(query));
        verifyNoInteractions(organizations);
    }
    @Test void companyWideScopeCanCoverAProjectWithoutDepartment() {
        project.setDepartmentId(null);
        when(organizations.inspectRoleScope(argThat(q -> q.departmentId() == null))).thenReturn(new UserCompanyDepartmentScopeRespDTO());
        assertNotNull(api.inspect(query));
    }
}
