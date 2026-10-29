package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.OfficeProjectSearchReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OfficeProjectSearchQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OfficeProjectSearchServiceTest {
    @Test void criteriaRemainLiteralAndLookupRechecksRevokedScope() {
        var projects = mock(ProjectMasterMapper.class);
        var organizations = mock(ProjectOrganizationScopeService.class);
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        var contracts = mock(ProjectContractQueryApi.class);
        when(contracts.findProjectIdsByExactContractNo(any())).thenReturn(Set.of(42L));
        var service = new OfficeProjectSearchService(projects, organizations, permissions, contracts);
        TenantContextHolder.setTenantId(1L);
        try (var identity = mockStatic(SecurityFrameworkUtils.class)) {
            identity.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            when(permissions.hasAnyPermissions(9L, "pms:project:query")).thenReturn(true);
            when(organizations.rows(any())).thenAnswer(call -> {
                ProjectOrganizationScopeService.Query query = call.getArgument(0);
                assertEquals(1L, query.tenantId()); assertEquals(9L, query.userId());
                return query.summaryOnly() ? List.of(new Organization(11L, 21L)) : List.of();
            });
            var project = new ProjectMasterDO(); project.setId(42L);
            when(projects.selectOfficeProjectPage(any())).thenReturn(new PageResult<>(List.of(project), 1L));
            var request = new OfficeProjectSearchReqVO(); request.setProjectName(" exact%_"); request.setContractNo("C-01");
            assertEquals("OFFICE_READONLY", service.search(request).getList().getFirst().getVisibility());
            var captured = ArgumentCaptor.forClass(OfficeProjectSearchQuery.class);
            verify(projects).selectOfficeProjectPage(captured.capture());
            assertEquals(" exact%_", captured.getValue().getExactProjectName());
            assertEquals("C-01", captured.getValue().getExactContractNo());
            assertEquals(Set.of(42L), captured.getValue().getContractProjectIds());
            assertEquals(List.of(new Organization(11L,21L)), captured.getValue().getOrganizations());
            assertEquals(42L, service.validate(new OfficeProjectSearchService.Lookup(42L," exact%_","C-01")).projectId());
            doReturn(List.of()).when(organizations).rows(any());
            assertThrows(ServiceException.class, () -> service.validate(new OfficeProjectSearchService.Lookup(42L," exact%_","C-01")));
            assertTrue(service.search(request).getList().isEmpty());
            verify(projects, times(2)).selectOfficeProjectPage(any());
            verify(contracts, times(2)).findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery("C-01"));
        } finally { TenantContextHolder.clear(); }
    }

    @Test void emptyCriteriaNeverReachTheDatabaseEvenForAnAuthenticatedManager() {
        var projects = mock(ProjectMasterMapper.class);
        var organizations = mock(ProjectOrganizationScopeService.class);
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        var contracts = mock(ProjectContractQueryApi.class);
        var service = new OfficeProjectSearchService(projects, organizations, permissions, contracts);
        TenantContextHolder.setTenantId(1L);
        try (var identity = mockStatic(SecurityFrameworkUtils.class)) {
            identity.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            var request = new OfficeProjectSearchReqVO(); request.setProjectName("  ");
            assertFalse(request.isExactCriterionPresent());
            assertThrows(ServiceException.class, () -> service.search(request));
            request.setContractNo("C"); request.setPageSize(-1);
            assertThrows(ServiceException.class, () -> service.search(request));
            request.setPageSize(10);
            assertThrows(ServiceException.class, () -> service.search(request));
            verify(permissions).hasAnyPermissions(9L, "pms:project:query");
            verifyNoInteractions(projects, organizations, contracts);
        } finally { TenantContextHolder.clear(); }
    }

    @Test void nameOnlyAvoidsCommerceButContractReadsRecheckAndFailClosedOnOwnerFailure() {
        var projects = mock(ProjectMasterMapper.class);
        var organizations = mock(ProjectOrganizationScopeService.class);
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        var contracts = mock(ProjectContractQueryApi.class);
        var service = new OfficeProjectSearchService(projects, organizations, permissions, contracts);
        TenantContextHolder.setTenantId(1L);
        try (var identity = mockStatic(SecurityFrameworkUtils.class)) {
            identity.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            when(permissions.hasAnyPermissions(9L, "pms:project:query")).thenReturn(true);
            when(organizations.rows(any())).thenReturn(List.of(new Organization(11L,21L)));
            var project = new ProjectMasterDO(); project.setId(42L);
            when(projects.selectOfficeProjectPage(any())).thenReturn(new PageResult<>(List.of(project),1L));
            var request = new OfficeProjectSearchReqVO(); request.setProjectName("Name");
            service.search(request);
            verifyNoInteractions(contracts);
            request.setContractNo(" C%_ ");
            when(contracts.findProjectIdsByExactContractNo(any())).thenReturn(Set.of(42L),Set.of());
            service.search(request);
            service.validate(new OfficeProjectSearchService.Lookup(42L,"Name"," C%_ "));
            var captured = ArgumentCaptor.forClass(OfficeProjectSearchQuery.class);
            verify(projects,times(3)).selectOfficeProjectPage(captured.capture());
            assertEquals(Set.of(42L),captured.getAllValues().get(1).getContractProjectIds());
            assertEquals(Set.of(),captured.getAllValues().get(2).getContractProjectIds());
            verify(contracts,times(2)).findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery(" C%_ "));
            when(contracts.findProjectIdsByExactContractNo(any())).thenThrow(new IllegalStateException("Owner unavailable"));
            assertThrows(IllegalStateException.class,()-> service.search(request));
            verify(projects,times(3)).selectOfficeProjectPage(any());
        } finally { TenantContextHolder.clear(); }
    }
}
