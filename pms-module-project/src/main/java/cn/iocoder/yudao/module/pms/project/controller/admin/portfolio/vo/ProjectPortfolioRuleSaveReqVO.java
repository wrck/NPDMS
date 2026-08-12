package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 项目组合动态规则 Request VO")
@Data
public class ProjectPortfolioRuleSaveReqVO {

    @Schema(description = "规则编号（更新时使用）", example = "1024")
    private Long id;

    @Schema(description = "规则字段 CUSTOMER/REGION/TYPE/STATUS", requiredMode = Schema.RequiredMode.REQUIRED, example = "CUSTOMER")
    @NotBlank(message = "规则字段不能为空")
    private String ruleField;

    @Schema(description = "规则操作符 EQ/NE/IN/LIKE", requiredMode = Schema.RequiredMode.REQUIRED, example = "EQ")
    @NotBlank(message = "规则操作符不能为空")
    private String ruleOperator;

    @Schema(description = "规则值（IN 用逗号分隔）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotBlank(message = "规则值不能为空")
    @Size(max = 500, message = "规则值长度不能超过 500 个字符")
    private String ruleValue;

}
