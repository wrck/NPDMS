package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface ProjectTaskMaintenanceMapper {
    record TaskQuery(Long tenantId, Long projectTaskId) { }
    record HistoryQuery(Long tenantId, Long projectTaskId, int offset, int limit) { }
    record CloseResponsible(Long tenantId, Long id, Integer version, LocalDateTime effectiveTo, String updater) { }
    record DescriptionUpdate(Long tenantId, Long taskId, Integer version, String updater, String text, String format) { }
    record Description(String description, String descriptionFormat) { }
    TaskResponsibleRow selectCurrentForUpdate(@Param("query") TaskQuery query);
    List<TaskResponsibleRow> selectHistory(@Param("query") HistoryQuery query);
    List<TaskResponsibleRow> selectExecutorHistory(@Param("query") HistoryQuery query);
    int insertResponsible(@Param("row") TaskResponsibleRow row);
    int closeResponsible(@Param("query") CloseResponsible query);
    Description selectDescription(@Param("query") TaskQuery query);
    int updateDescription(@Param("query") DescriptionUpdate query);
}
