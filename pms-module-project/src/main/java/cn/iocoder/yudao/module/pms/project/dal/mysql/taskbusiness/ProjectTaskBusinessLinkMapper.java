package cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessLinksQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessUnlinkUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectTaskBusinessLinkMapper {
    List<ProjectTaskBusinessLinkDO> selectActive(@Param("query") TaskBusinessLinksQuery query);
    List<ProjectTaskBusinessLinkDO> selectActiveForUpdate(@Param("query") TaskBusinessLinksQuery query);
    ProjectTaskExecutionContractDO selectCurrentContract(@Param("query") TaskBusinessLinksQuery query);
    int insertLink(@Param("link") ProjectTaskBusinessLinkDO link);
    int unlinkIfMatch(@Param("query") TaskBusinessUnlinkUpdate query);
}
