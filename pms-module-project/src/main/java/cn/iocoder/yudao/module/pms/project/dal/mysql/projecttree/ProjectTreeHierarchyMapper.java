package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectTreeParentsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectWaitTreeQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProjectTreeHierarchyMapper extends BaseMapperX<ProjectTreePathDO> {
    /** Include self so an unavailable projection cannot masquerade as no children. */
    default List<ProjectTreePathDO> selectWaitSubtree(ProjectWaitTreeQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectTreePathDO>()
                .eq(ProjectTreePathDO::getTenantId, query.tenantId())
                .eq(ProjectTreePathDO::getRootProjectId, query.rootProjectId())
                .eq(ProjectTreePathDO::getTreeVersion, query.treeVersion())
                .eq(ProjectTreePathDO::getAncestorProjectId, query.projectId())
                .orderByAsc(ProjectTreePathDO::getDistance, ProjectTreePathDO::getDescendantProjectId));
    }

    default List<ProjectTreePathDO> selectWaitAncestors(ProjectWaitTreeQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectTreePathDO>()
                .eq(ProjectTreePathDO::getTenantId, query.tenantId())
                .eq(ProjectTreePathDO::getRootProjectId, query.rootProjectId())
                .eq(ProjectTreePathDO::getTreeVersion, query.treeVersion())
                .eq(ProjectTreePathDO::getDescendantProjectId, query.projectId())
                .gt(ProjectTreePathDO::getDistance, 0)
                .orderByAsc(ProjectTreePathDO::getDistance, ProjectTreePathDO::getAncestorProjectId));
    }

    default List<ProjectTreePathDO> selectDirectParents(ProjectTreeParentsQuery query) {
        if (query.projectIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProjectTreePathDO>()
                .eq(ProjectTreePathDO::getTenantId, query.tenantId())
                .eq(ProjectTreePathDO::getRootProjectId, query.rootProjectId())
                .eq(ProjectTreePathDO::getTreeVersion, query.treeVersion())
                .eq(ProjectTreePathDO::getDistance, 1)
                .in(ProjectTreePathDO::getDescendantProjectId, query.projectIds()));
    }
}
