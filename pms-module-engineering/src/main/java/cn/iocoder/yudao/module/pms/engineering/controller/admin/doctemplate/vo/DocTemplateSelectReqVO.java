package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台 - PMS 工程文档模板选择 Request VO（V36 结构化文档模板）。
 * <p>
 * 按文档类别 + 项目条件（projectType/networkType/productType/implementMode）进行三级降级匹配。
 */
@Schema(description = "管理后台 - PMS 工程文档模板选择 Request VO")
@Data
public class DocTemplateSelectReqVO {

    @Schema(description = "文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案", requiredMode = Schema.RequiredMode.REQUIRED, example = "REQUIREMENT")
    @NotBlank(message = "文档类别不能为空")
    private String docCategory;

    @Schema(description = "项目类型", example = "NEW_BUILD")
    private String projectType;

    @Schema(description = "网络类型", example = "LAN")
    private String networkType;

    @Schema(description = "产品类型", example = "FIREWALL")
    private String productType;

    @Schema(description = "实施模式", example = "TURNKEY")
    private String implementMode;
}
