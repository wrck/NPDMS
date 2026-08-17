package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 项目模板创建 Request VO（F-PM03 BR-1）
 */
@Schema(description = "管理后台 - 项目模板创建 Request VO")
@Data
public class ProjectTemplateCreateReqVO {

    @Schema(description = "模板编码（租户内唯一，创建后不可修改）", requiredMode = Schema.RequiredMode.REQUIRED, example = "TPL-STD-DELIVERY")
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "标准交付模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "匹配优先级（数值小者先命中）", example = "100")
    private Integer matchPriority;

    @Schema(description = "业务场景描述", example = "适用于标准签约交付项目")
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;
}
