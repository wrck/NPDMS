package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 项目模板版本 Response VO（F-PM03 BR-3）
 */
@Schema(description = "管理后台 - 项目模板版本 Response VO")
@Data
public class ProjectTemplateRevisionRespVO {

    @Schema(description = "版本编号", example = "1")
    private Long id;

    @Schema(description = "模板编号", example = "1")
    private Long templateId;

    @Schema(description = "版本号（0=草稿工作副本，发布时递增冻结）", example = "1")
    private Integer revisionNo;

    @Schema(description = "状态：DRAFT草稿/PUBLISHED已发布", example = "PUBLISHED")
    private String status;

    @Schema(description = "匹配条件：签约方式（null=不限）", example = "CONTRACT")
    private String signingMethod;

    @Schema(description = "匹配条件：项目类别（null=不限）", example = "SOFTWARE")
    private String projectCategory;

    @Schema(description = "匹配条件：实施方式（null=不限）", example = "ONSITE")
    private String implementationMethod;

    @Schema(description = "匹配条件：重大项目级别（null=不限）", example = "LEVEL_A")
    private String majorProjectLevel;

    @Schema(description = "流程定义引用", example = "project_delivery_flow")
    private String processDefinitionKey;

    @Schema(description = "流程定义版本引用", example = "1")
    private String processDefinitionVersion;

    @Schema(description = "最近一次发布校验结果摘要（留痕）")
    private String validationSummary;

    @Schema(description = "发布人", example = "1")
    private String publishedBy;

    @Schema(description = "发布时间")
    private LocalDateTime publishedTime;
}
