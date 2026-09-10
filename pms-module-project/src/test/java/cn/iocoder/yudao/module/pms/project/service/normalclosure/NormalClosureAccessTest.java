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
    @Test void queryUsesRealViewScopeWithoutGrantingSubmission() {
        var projects = mock(ProjectMasterMapper.class);
        var scope = mock(ProjectScopeApi.class);
        var participants = mock(ProjectParticipantFactApi.class);
        var permissions = mock(PermissionApi.class);
        var access = new NormalClosureAccess(projects, mock(ProjectMemberAssignmentMapper.class), scope, participants, permissions);
        var project = new ProjectMasterDO(); project.setId(10L); project.setTenantId(1L); project.setManagerId(9L);
        when(projects.selectById(10L)).thenReturn(project);
        when(permissions.hasAnyPermissions(2L, NormalClosureAccess.QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(2L, NormalClosureAccess.SUBMIT)).thenReturn(true);
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(1L,2L,10L,ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        TenantContextHolder.setTenantId(1L);
        try {
            assertEquals(project, access.read(10L,new NormalClosureAccess.Actor(1L,2L,"view"),NormalClosureAccess.QUERY).project());
            verifyNoInteractions(participants);
            assertThrows(RuntimeException.class,()->access.read(10L,new NormalClosureAccess.Actor(1L,2L,"submit"),NormalClosureAccess.SUBMIT));
        } finally { TenantContextHolder.clear(); }
    }
}
