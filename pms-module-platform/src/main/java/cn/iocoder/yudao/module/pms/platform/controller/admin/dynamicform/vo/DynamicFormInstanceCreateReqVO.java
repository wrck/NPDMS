package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DynamicFormInstanceCreateReqVO {
    @NotNull @Positive
    private Long templateRevisionId;
    @NotNull @PositiveOrZero
    private Integer expectedTemplateVersion;
    @NotBlank @Size(max = 128)
    private String instanceName;
}
