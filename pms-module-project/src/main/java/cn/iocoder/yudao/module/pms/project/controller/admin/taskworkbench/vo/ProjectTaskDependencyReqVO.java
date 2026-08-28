package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectTaskDependencyReqVO {
    @NotNull private Long predecessorTaskId;
    @NotBlank private String dependencyTypeCode;
}
