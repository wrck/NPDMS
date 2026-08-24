package cn.iocoder.yudao.module.pms.project.service.projectclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.ProjectClosureMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosureGuardListQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ClosureStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProjectClosureStateAdapter implements ClosureStatePort {
    private static final int STATUS_PENDING_APPROVE = 1;
    private static final int STATUS_APPROVING = 2;

    private final ProjectMasterMapper projectMapper;
    private final ProjectClosureMapper closureMapper;

    @Override
    public Map<Long, ClosureState> findByProjectIds(Long tenantId, Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Map.of();
        Map<Long, ProjectClosureDO> latestClosures = new HashMap<>();
        ProjectClosureGuardListQuery query = new ProjectClosureGuardListQuery(tenantId, Set.copyOf(projectIds));
        closureMapper.selectListForClosureGuard(query).stream()
                .filter(closure -> Objects.equals(closure.getTenantId(), tenantId))
                .forEach(closure -> latestClosures.putIfAbsent(closure.getProjectId(), closure));
        Map<Long, ClosureState> result = new HashMap<>();
        for (ProjectMasterDO project : projectMapper.selectBatchIds(projectIds)) {
            if (!Objects.equals(project.getTenantId(), tenantId)) continue;
            result.put(project.getId(), resolve(project, latestClosures.get(project.getId())));
        }
        return Map.copyOf(result);
    }

    private ClosureState resolve(ProjectMasterDO project, ProjectClosureDO closure) {
        if ("NORMAL_CLOSED".equals(project.getLifecycleStatus())
                || "EXCEPTION_CLOSED".equals(project.getLifecycleStatus())) {
            return ClosureState.CLOSED;
        }
        if (closure == null || closure.getStatus() == null) return ClosureState.EXECUTING;
        if (List.of(STATUS_PENDING_APPROVE, STATUS_APPROVING).contains(closure.getStatus())) {
            return ClosureState.CLOSURE_APPROVING;
        }
        return ClosureState.EXECUTING;
    }
}
