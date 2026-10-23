package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectOverdueNodeFact;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStatusQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectOverdueMapper {
    List<ProjectOverdueNodeFact> selectStatusFacts(@Param("query") ProjectStatusQuery query);
}
