package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeHierarchyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectWaitTreeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectRuntimeCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProjectChildWaitDelivery {
    private final ProjectMasterMapper projects;
    private final ProjectTreeVersionMapper versions;
    private final ProjectTreeHierarchyMapper paths;
    private final ProjectRuntimeCoordinator coordinator;

    public boolean deliver(ProjectChildWaitEvents.Changed event) {
        var child = projects.selectById(event.projectId());
        if (child == null || !Objects.equals(event.tenantId(), child.getTenantId())) return false;
        if (child.getParentId() == null || child.getParentId() == 0) return true;
        Long root = child.getRootId();
        if (root == null) return false;
        var version = versions.selectLatestActive(root);
        if (version == null || !Objects.equals(version.getTenantId(), event.tenantId())) return false;
        var ancestors = paths.selectWaitAncestors(new ProjectWaitTreeQuery(event.tenantId(), root, version.getTreeVersion(), child.getId()));
        if (ancestors.stream().noneMatch(path -> Objects.equals(path.getAncestorProjectId(), child.getParentId()))) return false;
        boolean complete = true;
        for (var path : ancestors) {
            try {
                // Wake the ancestor's own frozen plan. No close command and no downward cascade.
                if (coordinator.reevaluate(path.getAncestorProjectId(), event.actorId(), event.correlationId()).unknown()) complete = false;
            } catch (RuntimeException unavailable) { complete = false; }
        }
        return complete;
    }
}
