package cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectRuntimeGraphMapper extends BaseMapperX<ProjectStageTransitionDO> {
    List<ProjectStageInstanceDO> selectStages(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectTaskInstanceDO> selectTasks(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectGateInstanceDO> selectGates(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectStageInstanceDO> selectStagesForUpdate(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectStageTransitionDO> selectTransitions(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectStageExecutionContractDO> selectContracts(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectTaskInstanceDO> selectTasksForUpdate(@Param("query") ProjectRuntimeGraphQuery query);
    List<ProjectGateInstanceDO> selectGatesForUpdate(@Param("query") ProjectRuntimeGraphQuery query);
}
