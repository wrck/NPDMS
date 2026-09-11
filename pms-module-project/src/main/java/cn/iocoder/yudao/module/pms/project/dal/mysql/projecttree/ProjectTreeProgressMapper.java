package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectTreeProgressQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectTreeProgressMapper {
    List<ProjectTreeProgressRow> selectRecordedProgress(@Param("query") ProjectTreeProgressQuery query);
}
