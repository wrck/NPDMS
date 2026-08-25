package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 项目业务属性人工调整 Request VO")
@Data
public class ProjectAttributeClassifyReqVO {

    @NotBlank
    private String signingMethod;
    @NotBlank
    private String projectCategory;
    @NotBlank
    private String implementationMode;
    @NotBlank
    private String adjustmentReason;
}
