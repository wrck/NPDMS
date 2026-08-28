package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreparationSourceRefreshReqVO {
    @NotNull private Integer expectedPreparationVersion;
    @NotNull private Integer expectedInputVersion;
    @NotNull private Integer expectedReadinessVersion;
    @NotNull private Integer expectedItemVersion;
    private Integer expectedSourceVersion;
    @NotNull private Integer expectedProjectVersion;
    @NotBlank private String sourceTypeCode;
    @NotBlank private String sourceObjectType;
    @NotBlank private String sourceObjectId;
    @NotBlank private String sourceReferenceKey;
}
