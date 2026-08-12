package cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接风险新增/修改 Request VO（FR-CUT-004）。
 */
@Schema(description = "管理后台 - 割接风险新增/修改 Request VO")
@Data
public class CutRiskSaveReqVO {

    @Schema(description = "风险编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "割接任务编号不能为空")
    private Long taskId;

    @Schema(description = "风险编码，任务内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "RSK20260101001")
    @NotBlank(message = "风险编码不能为空")
    @Size(max = 64, message = "风险编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "风险名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "业务中断风险")
    @NotBlank(message = "风险名称不能为空")
    @Size(max = 255, message = "风险名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "类型 RISK/SURVEY", example = "RISK")
    private String riskType;

    @Schema(description = "风险描述", example = "割接期间业务流量可能中断")
    private String description;

    @Schema(description = "影响分析", example = "影响核心交易系统 5 分钟")
    private String impact;

    @Schema(description = "缓解措施", example = "提前切换备用链路")
    private String mitigation;

    @Schema(description = "责任人编号", example = "1024")
    private Long ownerUserId;

    @Schema(description = "备注", example = "需二线确认")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
