package cn.iocoder.yudao.module.pms.project.dal.mysql.completion;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectAssignmentCompletionMapper {
    ProjectMasterDO project(@Param("query") ProjectAssignmentCompletionQuery query);
    List<ProjectMemberAssignmentDO> members(@Param("query") ProjectAssignmentCompletionQuery query);
}
