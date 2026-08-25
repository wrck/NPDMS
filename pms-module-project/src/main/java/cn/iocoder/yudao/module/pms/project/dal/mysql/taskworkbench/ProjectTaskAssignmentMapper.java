package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCloseUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 项目任务责任区间持久化入口。 */
@Mapper
public interface ProjectTaskAssignmentMapper {

    int insertAssignment(@Param("assignment") ProjectTaskAssignmentDO assignment);

    ProjectTaskAssignmentDO selectCurrentForUpdate(@Param("query") TaskAssignmentLockQuery query);

    int closeCurrentIfMatch(@Param("query") TaskAssignmentCloseUpdate update);
}
