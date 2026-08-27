package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RequirementAnalysisCreateReqVO {
    @NotNull @Positive private Long projectId;
    @NotBlank private String type;
    private Integer expectedProjectVersion;
}
