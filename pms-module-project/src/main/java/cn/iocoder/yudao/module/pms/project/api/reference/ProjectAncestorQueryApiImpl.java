package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectAncestorQueryApiImpl implements ProjectAncestorQueryApi {

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreePathMapper pathMapper;

    @Override
    public ProjectAncestorResult getAncestors(ProjectAncestorQuery query) {
        if (query == null || query.tenantId() == null || query.projectId() == null) {
            throw new IllegalArgumentException("项目祖先查询参数不能为空");
        }
        if (!Objects.equals(TenantContextHolder.getTenantId(), query.tenantId())) {
            throw new IllegalStateException("PROJECT_NOT_FOUND");
        }
        ProjectMasterDO project = projectMapper.selectById(query.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId())) {
            throw new IllegalStateException("PROJECT_NOT_FOUND");
        }
        Long rootProjectId = project.getRootId() == null ? project.getId() : project.getRootId();
        Long treeVersion = query.treeVersion();
        if (treeVersion == null) {
            var active = treeVersionMapper.selectLatestActive(rootProjectId);
            treeVersion = active == null ? null : active.getTreeVersion();
        } else if (treeVersionMapper.selectActiveVersion(rootProjectId, treeVersion) == null) {
            treeVersion = null;
        }
        if (treeVersion == null) {
            throw new IllegalStateException("TREE_VERSION_UNAVAILABLE");
        }
        List<Long> ancestorProjectIds = pathMapper.selectByDescendants(
                        rootProjectId, treeVersion, List.of(query.projectId())).stream()
                .filter(path -> path.getDistance() != null && path.getDistance() > 0)
                .sorted(Comparator.comparing(ProjectTreePathDO::getDistance).reversed())
                .map(ProjectTreePathDO::getAncestorProjectId)
                .toList();
        return new ProjectAncestorResult(
                query.projectId(), rootProjectId, treeVersion, ancestorProjectIds);
    }
}
