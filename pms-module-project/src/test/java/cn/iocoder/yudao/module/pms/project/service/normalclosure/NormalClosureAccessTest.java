package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalClosureAccessTest {
    @Test void nonPrimaryManagerAndRegionalManagerKeepActualResponsibilityAndRevalidateWrites() {
        var projects = mock(ProjectMasterMapper.class);
        var scope = mock(ProjectScopeApi.class);
        var permissions = mock(PermissionApi.class);
        var regional = mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class);
        var access = new NormalClosureAccess(projects,
                mock(cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper.class),
                scope, permissions, new NormalClosurePolicyResolver(
                    mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class)), regional);
        var project = new ProjectMasterDO(); project.setId(10L); project.setTenantId(1L);
        project.setManagerId(9L); project.setVersion(3L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectById(10L)).thenReturn(project);
        when(projects.selectByIdForUpdate(10L)).thenReturn(project);
        when(permissions.hasAnyPermissions(2L, NormalClosureAccess.SUBMIT)).thenReturn(true);
        when(scope.resolveCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(scope.lockAndRevalidate(org.mockito.ArgumentMatchers.any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(regional.permits(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var actor = new NormalClosureAccess.Actor(1L,2L,"submit");
        TenantContextHolder.setTenantId(1L);
        try {
            assertEquals(9L, access.read(10L, actor, NormalClosureAccess.SUBMIT).project().getManagerId());

            when(regional.permits(org.mockito.ArgumentMatchers.any())).thenReturn(true);
            assertEquals(project, access.read(10L, actor, NormalClosureAccess.SUBMIT).project());
            assertThrows(RuntimeException.class, () -> access.lock(10L,3L,1L,actor));
            when(regional.lockCurrent(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> cn.iocoder.yudao.module.pms.project.api.participant.ManagementQualificationTestFacts.locked(call.getArgument(0)));
            assertEquals(project, access.lock(10L,3L,1L,actor).project());
            verify(regional, times(2)).lockCurrent(org.mockito.ArgumentMatchers.any());
            assertEquals(9L, project.getManagerId());
        } finally { TenantContextHolder.clear(); }
    }

    @Test void queryUsesRealViewScopeWithoutGrantingSubmission() {
        var projects = mock(ProjectMasterMapper.class);
        var scope = mock(ProjectScopeApi.class);
        var permissions = mock(PermissionApi.class);
        var access = new NormalClosureAccess(projects, mock(cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper.class), scope, permissions,
                new NormalClosurePolicyResolver(mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class)), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
        var project = new ProjectMasterDO(); project.setId(10L); project.setTenantId(1L); project.setManagerId(9L);
        when(projects.selectById(10L)).thenReturn(project);
        when(permissions.hasAnyPermissions(2L, NormalClosureAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(2L, NormalClosureAccess.SUBMIT)).thenReturn(true);
        when(scope.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(1L)
                .subjectUserId(2L)
                .anchorProjectId(10L)
                .actionCode(ProjectScopeApi.ACTION_VIEW).build()))
                .thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        TenantContextHolder.setTenantId(1L);
        try {
            assertEquals(project, access.read(10L,new NormalClosureAccess.Actor(1L,2L,"view"),NormalClosureAccess.QUERY).project());
            assertThrows(RuntimeException.class,()->access.read(10L,new NormalClosureAccess.Actor(1L,2L,"submit"),NormalClosureAccess.SUBMIT));
        } finally { TenantContextHolder.clear(); }
    }
}
