package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TaskResponsibleRow {
    private Long id, projectId, projectTaskId, userId, assignedBy, tenantId;
    private LocalDateTime effectiveFrom, effectiveTo;
    private String reason;
    private Integer version;
}
