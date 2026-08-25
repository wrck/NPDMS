package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectTaskCreateReqVO {
    @NotBlank @Size(max = 64) private String taskCode;
    @NotBlank @Size(max = 128) private String name;
    @NotBlank @Size(max = 32) private String stageCode;
    private Long parentTaskId;
    @Size(max = 64) private String businessLevelCode;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Integer priority;
    private Integer sortOrder;
    @Size(max = 500) private String description;
}
