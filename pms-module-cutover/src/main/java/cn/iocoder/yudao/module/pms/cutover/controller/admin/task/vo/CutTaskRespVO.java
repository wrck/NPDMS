package cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 割接任务 Response VO（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 */
@Schema(description = "管理后台 - 割接任务 Response VO")
@Data
public class CutTaskRespVO {

    @Schema(description = "割接任务编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "割接任务编码", example = "CUT20260101001")
    private String code;

    @Schema(description = "割接任务名称", example = "核心交换机替换割接")
    private String name;

    @Schema(description = "割接类型", example = "REPLACE")
    private String cutoverType;

    @Schema(description = "组网模式", example = "VSM")
    private String networkMode;

    @Schema(description = "来源类型 PROJECT/ITR/MANUAL", example = "MANUAL")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "1")
    private Long sourceId;

    @Schema(description = "割接等级 A/B/C/D", example = "C")
    private String riskLevel;

    @Schema(description = "计划割接时间", example = "2026-01-01T10:00:00")
    private LocalDateTime scheduledTime;

    @Schema(description = "实际割接时间", example = "2026-01-01T10:05:00")
    private LocalDateTime actualTime;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "评审意见", example = "前置门禁已满足，同意推进")
    private String approvalOpinion;

    @Schema(description = "备注", example = "需提前72小时申请")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
