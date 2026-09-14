package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectReworkMapper {
    int retireEndedExecution(@Param("query") ProjectExecutionRetire query);
    int resetTaskProjection(@Param("query") ProjectReworkTaskReset query);
    int advanceProjectVersion(@Param("query") ProjectReworkVersionUpdate query);
}
