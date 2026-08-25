package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目任务状态机版本 Mapper。
 */
@Mapper
public interface TaskStateMachineMapper extends BaseMapperX<TaskStateMachineRevisionDO> {

    TaskStateMachineRevisionDO selectCurrentPublished(@Param("query") TaskStateMachinePublishedQuery query);
}
