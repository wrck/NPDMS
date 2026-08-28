package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectDeviceAssignmentGuardApiImpl implements ProjectDeviceAssignmentGuardApi {

    private final ProjectMasterMapper projectMasterMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreePathMapper projectTreePathMapper;
    private final ProjectScopeApi projectScopeApi;

    @Override
    public ProjectDeviceAssignmentGuardResult validate(ProjectDeviceAssignmentGuardQuery query) {
        if (query == null || query.tenantId() == null || query.projectId() == null || query.actorId() == null) {
            throw new IllegalArgumentException("项目设备归属守卫参数不能为空");
        }
        if (!Objects.equals(TenantContextHolder.getTenantId(), query.tenantId())) {
            return rejected(query, null, null, null, "PROJECT_NOT_FOUND");
        }
        ProjectMasterDO project = projectMasterMapper.selectById(query.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId())) {
            return rejected(query, null, null, null, "PROJECT_NOT_FOUND");
        }
        Long rootProjectId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO treeVersion = projectTreeVersionMapper.selectLatestActive(rootProjectId);
        if (treeVersion == null) {
            return rejected(query, project.getCustomerId(), rootProjectId, null, "TREE_VERSION_UNAVAILABLE");
        }
        Long currentTreeVersion = treeVersion.getTreeVersion();
        if (!projectTreePathMapper.selectParentsWithChildren(
                rootProjectId, currentTreeVersion, Set.of(project.getId())).isEmpty()) {
            return rejected(query, project.getCustomerId(), rootProjectId,
                    currentTreeVersion, "PROJECT_NOT_ACTUAL_NODE");
        }
        ProjectScopeResult scope = projectScopeApi.resolve(new ProjectScopeQuery(
                query.tenantId(), query.actorId(), query.projectId(),
                ProjectScopeApi.ACTION_MANAGE, currentTreeVersion));
        if (scope == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(query.projectId())) {
            return rejected(query, project.getCustomerId(), rootProjectId,
                    currentTreeVersion, "PROJECT_MANAGE_FORBIDDEN");
        }
        return new ProjectDeviceAssignmentGuardResult(
                project.getId(), project.getTenantId(), project.getCustomerId(),
                rootProjectId, currentTreeVersion, true, null);
    }

    private static ProjectDeviceAssignmentGuardResult rejected(
            ProjectDeviceAssignmentGuardQuery query,
            Long customerId,
            Long rootProjectId,
            Long treeVersion,
            String rejectionCode) {
        return new ProjectDeviceAssignmentGuardResult(
                query.projectId(), query.tenantId(), customerId, rootProjectId,
                treeVersion, false, rejectionCode);
    }
}
