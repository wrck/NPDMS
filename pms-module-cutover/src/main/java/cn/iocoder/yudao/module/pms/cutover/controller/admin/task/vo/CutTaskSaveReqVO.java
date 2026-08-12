package cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接任务新增/修改 Request VO（FR-CUT-001 / FR-CUT-002 / FR-CUT-003）。
 */
@Schema(description = "管理后台 - 割接任务新增/修改 Request VO")
@Data
public class CutTaskSaveReqVO {

    @Schema(description = "割接任务编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "割接任务编码，项目内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "CUT20260101001")
    @NotBlank(message = "割接任务编码不能为空")
    @Size(max = 64, message = "割接任务编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "割接任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机替换割接")
    @NotBlank(message = "割接任务名称不能为空")
    @Size(max = 128, message = "割接任务名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "割接类型", example = "REPLACE")
    private String cutoverType;

    @Schema(description = "组网模式", example = "VSM")
    private String networkMode;

    @Schema(description = "来源类型", example = "MANUAL")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "1")
    private Long sourceId;

    @Schema(description = "割接等级 A/B/C/D", example = "C")
    private String riskLevel;

    @Schema(description = "计划割接时间", example = "2026-01-01T10:00:00")
    private java.time.LocalDateTime scheduledTime;

    @Schema(description = "实际割接时间", example = "2026-01-01T10:05:00")
    private java.time.LocalDateTime actualTime;

    @Schema(description = "备注", example = "需提前72小时申请")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
