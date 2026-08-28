package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 项目模板四维匹配预演 Request VO（F-PM03 BR-4）
 */
@Schema(description = "管理后台 - 项目模板四维匹配预演 Request VO")
@Data
public class ProjectTemplateMatchPreviewReqVO {

    @Schema(description = "签约方式（来源项目实际值）", example = "CONTRACT")
    private String signingMethod;

    @Schema(description = "项目类别（来源项目实际值）", example = "SOFTWARE")
    private String projectCategory;

    @Schema(description = "实施方式（来源项目实际值）", example = "ONSITE")
    private String implementationMethod;

    @Schema(description = "重大项目级别（来源项目实际值）", example = "LEVEL_A")
    private String majorProjectLevel;
}
