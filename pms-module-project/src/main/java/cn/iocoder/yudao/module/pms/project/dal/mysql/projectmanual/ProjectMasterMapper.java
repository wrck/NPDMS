package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目主档 Mapper（F-PM01 / V57；F-PM02 树查询扩展）
 */
@Mapper
public interface ProjectMasterMapper extends BaseMapperX<ProjectMasterDO> {

    /**
     * 分页查询（简单条件）：名称模糊、编码/状态/三维精确，id 倒序
     */
    default PageResult<ProjectMasterDO> selectPage(PageParam pageParam, String projectName, String projectCode,
                                                   String status, String signingMethod, String projectCategory,
                                                   String implementationMode) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ProjectMasterDO>()
                .likeIfPresent(ProjectMasterDO::getProjectName, projectName)
                .likeRightIfPresent(ProjectMasterDO::getProjectCode, projectCode)
                .eqIfPresent(ProjectMasterDO::getStatus, status)
                .eqIfPresent(ProjectMasterDO::getSigningMethod, signingMethod)
                .eqIfPresent(ProjectMasterDO::getProjectCategory, projectCategory)
                .eqIfPresent(ProjectMasterDO::getImplementationMode, implementationMode)
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
