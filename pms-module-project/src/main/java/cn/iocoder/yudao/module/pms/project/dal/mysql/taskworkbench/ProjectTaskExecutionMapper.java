package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskExecutionLookupQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectTaskExecutionMapper {
    ProjectTaskExecutionRecord selectCurrent(@Param("query") ProjectTaskExecutionLookupQuery query);
    ProjectTaskExecutionRecord selectCurrentForUpdate(@Param("query") ProjectTaskExecutionLookupQuery query);
}
