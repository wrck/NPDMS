package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProjectTaskDetailRespVO {
    private Long taskId;
    private Long projectId;
    private String taskCode;
    private String name;
    private Long parentTaskId;
    private Long rootTaskId;
    private Integer treeDepth;
    private String businessLevelCode;
    private String stageCode;
    private String status;
    private Integer priority;
    private Integer sortOrder;
    private BigDecimal progress;
    private BigDecimal estimatedHours;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private String description;
    private Long assigneeUserId;
    private Integer version;
}
