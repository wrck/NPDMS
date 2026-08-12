package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 工程文档模板新增/修改 Request VO（V36 结构化文档模板）。
 */
@Schema(description = "管理后台 - PMS 工程文档模板新增/修改 Request VO")
@Data
public class DocTemplateSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "模板编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "DT-REQ-2026-001")
    @NotBlank(message = "模板编号不能为空")
    @Size(max = 64, message = "模板编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "标准需求分析模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案", requiredMode = Schema.RequiredMode.REQUIRED, example = "REQUIREMENT")
    @NotBlank(message = "文档类别不能为空")
    @Size(max = 32, message = "文档类别长度不能超过 32 个字符")
    private String docCategory;

    @Schema(description = "父模板ID（支持继承，NULL表示基础模板）", example = "1024")
    private Long parentTemplateId;

    @Schema(description = "适用条件JSON：projectType/networkType/productType/implementMode/priority/isDefault", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "适用条件不能为空")
    private String applicability;

    @Schema(description = "模板说明", example = "适用于网络设备的标准需求分析模板")
    @Size(max = 500, message = "模板说明长度不能超过 500 个字符")
    private String description;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
