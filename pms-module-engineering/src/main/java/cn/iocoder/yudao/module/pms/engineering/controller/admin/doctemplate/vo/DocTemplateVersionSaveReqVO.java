package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 工程文档模板版本新增/修改 Request VO（V36 结构化文档模板）。
 */
@Schema(description = "管理后台 - PMS 工程文档模板版本新增/修改 Request VO")
@Data
public class DocTemplateVersionSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "2048")
    private Long id;

    @Schema(description = "模板ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @Schema(description = "版本标签（SemVer，如 1.0.0）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0.0")
    @NotBlank(message = "版本标签不能为空")
    @Size(max = 32, message = "版本标签长度不能超过 32 个字符")
    private String versionLabel;

    @Schema(description = "章节定义JSON（sections数组，每个section含code/title/fields的form-create规则）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "章节定义不能为空")
    private String sections;

    @Schema(description = "相对父模板的章节覆盖声明（key=章节编码，value=覆盖配置）")
    private String sectionOverrides;

    @Schema(description = "排除的父模板章节编码列表（如 [\"wireless\",\"log_retention\"]）")
    private String excludedSections;

    @Schema(description = "版本变更说明")
    private String changeLog;
}
