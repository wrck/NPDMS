package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectTaskMoveReqVO {
    private Long targetParentTaskId;
    @NotNull @PositiveOrZero private Long expectedTaskTreeVersion;
    @NotBlank @Size(max = 500) private String reason;
}
