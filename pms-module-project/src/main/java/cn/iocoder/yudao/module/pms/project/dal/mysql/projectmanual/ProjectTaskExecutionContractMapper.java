package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectTaskExecutionContractMapper extends BaseMapperX<ProjectTaskExecutionContractDO> {

    default ProjectTaskExecutionContractDO selectCurrentByTaskId(Long taskId) {
        return selectOne(new LambdaQueryWrapperX<ProjectTaskExecutionContractDO>()
                .eq(ProjectTaskExecutionContractDO::getProjectTaskId, taskId)
                .isNull(ProjectTaskExecutionContractDO::getEffectiveTo));
    }

    ProjectTaskExecutionContractDO selectCurrentByTaskIdForUpdate(
            @Param("query") CurrentTaskExecutionContractLockQuery query);
}
