package cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate.query.ProjectLocalGateFactQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectLocalStageGateFactMapper {

    ProjectTaskInstanceDO selectTaskForUpdate(@Param("query") ProjectLocalGateFactQuery query);

    ProjectMilestoneInstanceDO selectMilestoneForUpdate(@Param("query") ProjectLocalGateFactQuery query);

    ProjectStageInstanceDO selectStageForUpdate(@Param("query") ProjectLocalGateFactQuery query);
}
