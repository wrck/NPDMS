package cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 项目风险新增/修改 Request VO（FR-PROJ-026）。
 */
@Schema(description = "管理后台 - 项目风险新增/修改 Request VO")
@Data
public class ProjectRiskSaveReqVO {

    @Schema(description = "风险编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "风险标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求频繁变更风险")
    @NotBlank(message = "风险标题不能为空")
    @Size(max = 200, message = "风险标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "风险等级 HIGH/MEDIUM/LOW", requiredMode = Schema.RequiredMode.REQUIRED, example = "HIGH")
    @NotBlank(message = "风险等级不能为空")
    @Size(max = 32, message = "风险等级长度不能超过 32 个字符")
    private String riskLevel;

    @Schema(description = "风险类型", example = "需求风险")
    @Size(max = 64, message = "风险类型长度不能超过 64 个字符")
    private String riskType;

    @Schema(description = "风险原因", example = "客户业务流程未明确")
    @Size(max = 500, message = "风险原因长度不能超过 500 个字符")
    private String cause;

    @Schema(description = "风险影响", example = "可能影响交付进度")
    @Size(max = 500, message = "风险影响长度不能超过 500 个字符")
    private String impact;

    @Schema(description = "缓解措施", example = "增加需求评审频次")
    @Size(max = 1000, message = "缓解措施长度不能超过 1000 个字符")
    private String mitigation;

    @Schema(description = "应急措施", example = "启用备用方案")
    @Size(max = 1000, message = "应急措施长度不能超过 1000 个字符")
    private String contingency;

    @Schema(description = "风险负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "状态：0 已识别 1 处理中 2 已关闭 3 已发生", example = "0")
    private Integer status;

    @Schema(description = "预警阈值", example = "影响 3 个及以上里程碑")
    @Size(max = 200, message = "预警阈值长度不能超过 200 个字符")
    private String warningThreshold;

    @Schema(description = "复核备注", example = "本月已复核")
    @Size(max = 500, message = "复核备注长度不能超过 500 个字符")
    private String reviewNotes;

    @Schema(description = "识别时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime identifiedAt;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
