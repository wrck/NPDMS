package cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 割接方案 Response VO（FR-CUT-008 / FR-CUT-009）。
 */
@Schema(description = "管理后台 - 割接方案 Response VO")
@Data
public class CutPlanRespVO {

    @Schema(description = "方案编号", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "方案编码", example = "PLN20260101001")
    private String code;

    @Schema(description = "方案名称", example = "核心交换机替换方案")
    private String name;

    @Schema(description = "割接前检查项", example = "确认备用链路状态正常")
    private String preCheck;

    @Schema(description = "割接步骤", example = "1. 下线主用 2. 切换备用 3. 验证")
    private String procedure;

    @Schema(description = "业务测试与验证", example = "全量业务联调通过")
    private String verification;

    @Schema(description = "回退方案", example = "回退到割接前快照")
    private String rollback;

    @Schema(description = "方案等级 A/B/C/D", example = "C")
    private String level;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "审核人编号", example = "1024")
    private Long approvedBy;

    @Schema(description = "审核时间", example = "2026-01-01T10:00:00")
    private LocalDateTime approvedTime;

    @Schema(description = "审核意见", example = "方案完整可执行")
    private String approvalOpinion;

    @Schema(description = "基线版本号，审核通过后形成不可覆盖基线", example = "1")
    private Integer baselineVersion;

    @Schema(description = "备注", example = "需提前演练")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
