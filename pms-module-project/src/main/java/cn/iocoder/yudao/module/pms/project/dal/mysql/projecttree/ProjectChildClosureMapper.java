package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectChildClosureFactsQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProjectChildClosureMapper extends BaseMapperX<ProjectMasterDO> {
    default List<ProjectMasterDO> selectClosureFacts(ProjectChildClosureFactsQuery query) {
        if (query.projectIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProjectMasterDO>()
                .select(ProjectMasterDO::getId, ProjectMasterDO::getTenantId, ProjectMasterDO::getLifecycleStatus)
                .eq(ProjectMasterDO::getTenantId, query.tenantId()).in(ProjectMasterDO::getId, query.projectIds())
                .orderByAsc(ProjectMasterDO::getId));
    }
}
