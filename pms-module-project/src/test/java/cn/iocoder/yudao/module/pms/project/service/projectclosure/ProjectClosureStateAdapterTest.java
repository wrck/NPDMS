package cn.iocoder.yudao.module.pms.project.service.projectclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.ProjectClosureMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosureGuardListQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ClosureStatePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectClosureStateAdapterTest {

    @Test
    void unknownLifecycleCannotBeMistakenForClosed() {
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectClosureMapper closureMapper = mock(ProjectClosureMapper.class);
        ProjectClosureStateAdapter adapter = new ProjectClosureStateAdapter(projectMapper, closureMapper);
        Set<Long> ids = Set.of(11L);
        when(projectMapper.selectBatchIds(ids)).thenReturn(List.of(project(11L, null)));
        when(closureMapper.selectListForClosureGuard(new ProjectClosureGuardListQuery(0L, ids)))
                .thenReturn(List.of());

        Map<Long, ClosureStatePort.ClosureState> result = adapter.findByProjectIds(0L, ids);

        assertEquals(ClosureStatePort.ClosureState.EXECUTING, result.get(11L));
    }

    @Test
    void closureApprovalMapsToBlockingAndLegacyPassedCannotOverrideLifecycle() {
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectClosureMapper closureMapper = mock(ProjectClosureMapper.class);
        ProjectClosureStateAdapter adapter = new ProjectClosureStateAdapter(projectMapper, closureMapper);
        Set<Long> ids = Set.of(11L, 12L, 13L);
        when(projectMapper.selectBatchIds(ids)).thenReturn(List.of(project(11L, "ACTIVE"),
                project(12L, "ACTIVE"), project(13L, "NORMAL_CLOSED")));
        when(closureMapper.selectListForClosureGuard(new ProjectClosureGuardListQuery(0L, ids)))
                .thenReturn(List.of(closure(11L, 2), closure(12L, 3)));

        Map<Long, ClosureStatePort.ClosureState> result = adapter.findByProjectIds(0L, ids);

        assertEquals(ClosureStatePort.ClosureState.CLOSURE_APPROVING, result.get(11L));
        assertEquals(ClosureStatePort.ClosureState.EXECUTING, result.get(12L));
        assertEquals(ClosureStatePort.ClosureState.CLOSED, result.get(13L));
    }

    private static ProjectMasterDO project(Long id, String lifecycleStatus) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setTenantId(0L);
        project.setLifecycleStatus(lifecycleStatus);
        return project;
    }

    private static ProjectClosureDO closure(Long projectId, int status) {
        ProjectClosureDO closure = new ProjectClosureDO();
        closure.setProjectId(projectId);
        closure.setTenantId(0L);
        closure.setStatus(status);
        return closure;
    }
}
