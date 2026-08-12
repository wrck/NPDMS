package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - PMS 项目组合 Response VO")
@Data
public class ProjectPortfolioRespVO {

    @Schema(description = "组合编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "组合编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PF001")
    private String code;

    @Schema(description = "组合名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "华东战略组合")
    private String name;

    @Schema(description = "组合用途", example = "战略")
    private String purpose;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "有效期开始", example = "2026-01-01")
    private LocalDate validFrom;

    @Schema(description = "有效期结束", example = "2026-12-31")
    private LocalDate validTo;

    @Schema(description = "状态：0草稿 1已发布 2已归档", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "统计目标（JSON 文本）", example = "{\"count\":10}")
    private String targetMetrics;

    @Schema(description = "成员类型 STATIC 静态 / DYNAMIC 动态", requiredMode = Schema.RequiredMode.REQUIRED, example = "STATIC")
    private String memberType;

    @Schema(description = "动态规则列表")
    private List<ProjectPortfolioRuleRespVO> rules;

    @Schema(description = "成员数量", example = "5")
    private Integer memberCount;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
