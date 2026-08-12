package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 工程文档模板版本 Response VO（V36 结构化文档模板）。
 */
@Schema(description = "管理后台 - PMS 工程文档模板版本 Response VO")
@Data
public class DocTemplateVersionRespVO {

    @Schema(description = "主键", example = "2048")
    private Long id;

    @Schema(description = "模板ID", example = "1024")
    private Long templateId;

    @Schema(description = "版本标签（SemVer，如 1.0.0）", example = "1.0.0")
    private String versionLabel;

    @Schema(description = "章节定义JSON（sections数组，每个section含code/title/fields的form-create规则）")
    private String sections;

    @Schema(description = "相对父模板的章节覆盖声明（key=章节编码，value=覆盖配置）")
    private String sectionOverrides;

    @Schema(description = "排除的父模板章节编码列表（如 [\"wireless\",\"log_retention\"]）")
    private String excludedSections;

    @Schema(description = "版本变更说明")
    private String changeLog;

    @Schema(description = "0 未发布 1 已发布", example = "0")
    private Integer published;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
