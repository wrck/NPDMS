package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 工程文档模板 Response VO（V36 结构化文档模板）。
 */
@Schema(description = "管理后台 - PMS 工程文档模板 Response VO")
@Data
public class DocTemplateRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "模板编号", example = "DT-REQ-2026-001")
    private String code;

    @Schema(description = "模板名称", example = "标准需求分析模板")
    private String name;

    @Schema(description = "文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案", example = "REQUIREMENT")
    private String docCategory;

    @Schema(description = "父模板ID（支持继承，NULL表示基础模板）", example = "1024")
    private Long parentTemplateId;

    @Schema(description = "适用条件JSON：projectType/networkType/productType/implementMode/priority/isDefault")
    private String applicability;

    @Schema(description = "模板说明")
    private String description;

    @Schema(description = "当前生效版本ID", example = "2048")
    private Long currentVersionId;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "0")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
