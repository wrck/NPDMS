package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - PMS 项目组合创建/修改 Request VO")
@Data
public class ProjectPortfolioSaveReqVO {

    @Schema(description = "组合编号", example = "1024")
    private Long id;

    @Schema(description = "组合编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PF001")
    @NotBlank(message = "组合编码不能为空")
    @Size(max = 64, message = "组合编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "组合名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "华东战略组合")
    @NotBlank(message = "组合名称不能为空")
    @Size(max = 128, message = "组合名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "组合用途", example = "战略")
    @Size(max = 500, message = "组合用途长度不能超过 500 个字符")
    private String purpose;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "有效期开始", example = "2026-01-01")
    private LocalDate validFrom;

    @Schema(description = "有效期结束", example = "2026-12-31")
    private LocalDate validTo;

    @Schema(description = "统计目标（JSON 文本）", example = "{\"count\":10}")
    private String targetMetrics;

    @Schema(description = "成员类型 STATIC 静态 / DYNAMIC 动态", requiredMode = Schema.RequiredMode.REQUIRED, example = "STATIC")
    @NotBlank(message = "成员类型不能为空")
    private String memberType;

    @Schema(description = "静态成员项目编号列表（memberType=STATIC 时使用）")
    private List<Long> staticProjectIds;

    @Schema(description = "动态规则列表（memberType=DYNAMIC 时使用）")
    @Valid
    private List<ProjectPortfolioRuleSaveReqVO> rules;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
