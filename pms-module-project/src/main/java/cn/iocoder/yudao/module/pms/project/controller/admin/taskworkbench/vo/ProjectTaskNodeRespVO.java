package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProjectTaskNodeRespVO {
    private Long taskId;
    private Long projectId;
    private Long parentTaskId;
    private Long rootTaskId;
    private Integer treeDepth;
    private boolean placeholder;
    private String taskCode;
    private String name;
    private String stageCode;
    private String businessLevelCode;
    private String status;
    private Integer priority;
    private Integer sortOrder;
    private BigDecimal progress;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Long assigneeUserId;
    private String description;
    private Integer version;
}
