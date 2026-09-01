package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CreatedProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectBusinessAttributeUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageAdvanceUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.VisibleProjectPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目主档 Mapper（F-PM01 / V57；F-PM02 树查询扩展）
 */
@Mapper
public interface ProjectMasterMapper extends BaseMapperX<ProjectMasterDO> {

    default ProjectMasterDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getId, id));
    }

    default List<ProjectMasterDO> selectByIdsForUpdate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectListByIdsForUpdate(ids);
    }

    List<ProjectMasterDO> selectListByIdsForUpdate(@Param("ids") List<Long> ids);

    default List<ProjectMasterDO> selectTreeByRootId(Long rootId) {
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getRootId, rootId)
                .orderByAsc(ProjectMasterDO::getTreeDepth, ProjectMasterDO::getTreeSort, ProjectMasterDO::getId));
    }

    int incrementVersionIfMatch(@Param("projectId") Long projectId,
                                @Param("expectedVersion") Integer expectedVersion);

    int updateAssignmentStatusIfVersion(@Param("query") ProjectAssignmentStatusUpdate query);

    int updateBusinessAttributesIfMatch(@Param("query") ProjectBusinessAttributeUpdate query);

    int updateGovernanceStateIfMatch(@Param("query") ProjectGovernanceStateUpdate query);

    int advanceStageIfMatch(@Param("query") ProjectStageAdvanceUpdate query);

    /** F-PROJ-001创建人只读基础范围；空候选集合不得扩大为租户全量。 */
    default List<ProjectMasterDO> selectListCreatedBy(CreatedProjectScopeQuery query) {
        if (query.candidateProjectIds() != null && query.candidateProjectIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getTenantId, query.tenantId())
                .eq(ProjectMasterDO::getCreator, query.creatorId())
                .inIfPresent(ProjectMasterDO::getId, query.candidateProjectIds())
                .orderByAsc(ProjectMasterDO::getId));
    }

    /** 服务端范围过滤后的项目分页；空权限集合必须返回空页。 */
    default PageResult<ProjectMasterDO> selectPage(VisibleProjectPageQuery query) {
        if (query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        return selectPage(query.pageParam(), new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getTenantId, query.tenantId())
                .in(ProjectMasterDO::getId, query.visibleProjectIds())
                .likeIfPresent(ProjectMasterDO::getProjectName, query.projectNameKeyword())
                .likeRightIfPresent(ProjectMasterDO::getProjectCode, query.projectCodePrefix())
                .eqIfPresent(ProjectMasterDO::getStatus, query.status())
                .eqIfPresent(ProjectMasterDO::getSigningMethod, query.signingMethod())
                .eqIfPresent(ProjectMasterDO::getProjectCategory, query.projectCategory())
                .eqIfPresent(ProjectMasterDO::getImplementationMode, query.implementationMode())
                .orderByDesc(ProjectMasterDO::getId));
    }

    /**
     * 直接下级（按 tree_sort、id 升序；按需加载）
     */
    default List<ProjectMasterDO> selectChildren(Long parentId) {
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getParentId, parentId)
                .orderByAsc(ProjectMasterDO::getTreeSort)
                .orderByAsc(ProjectMasterDO::getId));
    }

    /**
     * 全部后代（tree_path 前缀匹配 + root_id 兜底过滤）
     */
    default List<ProjectMasterDO> selectDescendants(Long rootId, String treePath) {
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getRootId, rootId)
                .likeRight(ProjectMasterDO::getTreePath, treePath)
                .orderByAsc(ProjectMasterDO::getTreeDepth)
                .orderByAsc(ProjectMasterDO::getTreeSort)
                .orderByAsc(ProjectMasterDO::getId));
    }

    /**
     * 指定业务层级（business_level_code 精确，按结构深度排序）
     */
    default List<ProjectMasterDO> selectByBusinessLevel(String businessLevelCode) {
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .eq(ProjectMasterDO::getBusinessLevelCode, businessLevelCode)
                .orderByAsc(ProjectMasterDO::getTreeDepth)
                .orderByAsc(ProjectMasterDO::getId));
    }
}
