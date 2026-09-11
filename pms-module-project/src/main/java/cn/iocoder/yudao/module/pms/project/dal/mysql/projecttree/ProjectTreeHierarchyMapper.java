package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectTreeParentsQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProjectTreeHierarchyMapper extends BaseMapperX<ProjectTreePathDO> {
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
