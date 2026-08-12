package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - PMS 项目组合动态规则 Response VO")
@Data
public class ProjectPortfolioRuleRespVO {

    @Schema(description = "规则编号", example = "1024")
    private Long id;

    @Schema(description = "组合编号", example = "1")
    private Long portfolioId;

    @Schema(description = "规则字段 CUSTOMER/REGION/TYPE/STATUS", example = "CUSTOMER")
    private String ruleField;

    @Schema(description = "规则操作符 EQ/NE/IN/LIKE", example = "EQ")
    private String ruleOperator;

    @Schema(description = "规则值（IN 用逗号分隔）", example = "1")
    private String ruleValue;

}
