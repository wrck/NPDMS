package cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyItemDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import java.util.Collection;
import java.util.List;
@Mapper
public interface ProjectProgressPolicyItemMapper extends BaseMapperX<ProjectProgressPolicyItemDO> {
    default List<ProjectProgressPolicyItemDO> selectByRevisionId(Long revisionId) {
        return selectList(new LambdaQueryWrapperX<ProjectProgressPolicyItemDO>()
                .eq(ProjectProgressPolicyItemDO::getPolicyRevisionId, revisionId)
                .orderByAsc(ProjectProgressPolicyItemDO::getChildProjectId));
    }

    default List<ProjectProgressPolicyItemDO> selectByRevisionIds(Collection<Long> revisionIds) {
        if (revisionIds == null || revisionIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ProjectProgressPolicyItemDO>()
                .in(ProjectProgressPolicyItemDO::getPolicyRevisionId, revisionIds)
                .orderByAsc(ProjectProgressPolicyItemDO::getPolicyRevisionId,
                        ProjectProgressPolicyItemDO::getChildProjectId));
    }
}
