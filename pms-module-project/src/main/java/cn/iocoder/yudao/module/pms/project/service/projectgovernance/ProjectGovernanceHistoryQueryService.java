package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

/** 按受信租户和ProjectTreeScope读取PM-10 append-only治理快照。 */
@Service
@RequiredArgsConstructor
public class ProjectGovernanceHistoryQueryService {

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectTreeScopeService treeScopeService;
    private final ProjectStageSnapshotMapper snapshotMapper;

    public PageResult<ProjectStageSnapshotDO> page(
            ProjectGovernanceHistoryPageQuery query, Actor actor) {
        validate(query, actor);
        ProjectMasterDO project = projectMapper.selectById(query.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO tree = treeVersionMapper.selectLatestActive(rootId);
        if (tree == null || !Objects.equals(tree.getTenantId(), actor.tenantId())
                || tree.getTreeVersion() == null || tree.getTreeVersion() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        treeScopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), query.projectId(), ACTION_VIEW, tree.getTreeVersion()));
        return snapshotMapper.selectGovernanceHistoryPage(query);
    }

    private static void validate(ProjectGovernanceHistoryPageQuery query, Actor actor) {
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (query == null || query.tenantId() == null || query.tenantId() < 0
                || query.projectId() == null || query.projectId() <= 0
                || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || !Objects.equals(query.tenantId(), actor.tenantId())
                || contextTenantId != null && !Objects.equals(contextTenantId, actor.tenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    public record Actor(Long tenantId, Long actorId) {
    }
}
