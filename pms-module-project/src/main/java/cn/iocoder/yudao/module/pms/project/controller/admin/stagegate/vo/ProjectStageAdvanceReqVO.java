package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ProjectStageAdvanceReqVO {
    @NotBlank
    private String expectedCurrentStage;
    @NotNull
    @PositiveOrZero
    private Long expectedTreeVersion;
}
