package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectTaskUpdateReqVO {
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 64) private String businessLevelCode;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Integer priority;
    private Integer sortOrder;
    @Size(max = 500) private String description;
}
