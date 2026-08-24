package cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface ProjectProgressPolicyRevisionMapper extends BaseMapperX<ProjectProgressPolicyRevisionDO> {
    default ProjectProgressPolicyRevisionDO selectLatestByParentForUpdate(Long parentProjectId) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getParentProjectId, parentProjectId)
                .orderByDesc(ProjectProgressPolicyRevisionDO::getRevisionNo).last("LIMIT 1"));
    }

    default ProjectProgressPolicyRevisionDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getId, id));
    }

    default ProjectProgressPolicyRevisionDO selectByProcessInstanceIdForUpdate(String processInstanceId) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getProcessInstanceId, processInstanceId));
    }

    default ProjectProgressPolicyRevisionDO selectActiveByParent(Long parentProjectId) {
        return selectOne(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getParentProjectId, parentProjectId)
                .eq(ProjectProgressPolicyRevisionDO::getStatus, "ACTIVE")
                .isNull(ProjectProgressPolicyRevisionDO::getEffectiveTo)
                .orderByDesc(ProjectProgressPolicyRevisionDO::getRevisionNo).last("LIMIT 1"));
    }

    default ProjectProgressPolicyRevisionDO selectActiveByParentForUpdate(Long parentProjectId) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getParentProjectId, parentProjectId)
                .eq(ProjectProgressPolicyRevisionDO::getStatus, "ACTIVE")
                .isNull(ProjectProgressPolicyRevisionDO::getEffectiveTo)
                .orderByDesc(ProjectProgressPolicyRevisionDO::getRevisionNo).last("LIMIT 1"));
    }

    default List<ProjectProgressPolicyRevisionDO> selectListByParent(Long parentProjectId) {
        return selectList(new LambdaQueryWrapperX<ProjectProgressPolicyRevisionDO>()
                .eq(ProjectProgressPolicyRevisionDO::getParentProjectId, parentProjectId)
                .orderByDesc(ProjectProgressPolicyRevisionDO::getRevisionNo));
    }
}
