package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskDependencyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskDependencyPathQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 项目任务基础依赖持久化入口。 */
@Mapper
public interface ProjectTaskDependencyMapper extends BaseMapperX<ProjectTaskDependencyDO> {

    boolean existsDependencyPath(@Param("query") TaskDependencyPathQuery query);
}
