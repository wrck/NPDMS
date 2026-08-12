package cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 稳定观察 Response VO（FR-CUT-013 / FR-CUT-014）。
 */
@Schema(description = "管理后台 - 稳定观察 Response VO")
@Data
public class CutObservationRespVO {

    @Schema(description = "观察编号", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "观察编码", example = "OBS20260101001")
    private String code;

    @Schema(description = "观察开始时间", example = "2026-01-01T10:00:00")
    private LocalDateTime observationStart;

    @Schema(description = "观察结束时间", example = "2026-01-02T10:00:00")
    private LocalDateTime observationEnd;

    @Schema(description = "观察人编号", example = "1024")
    private Long observerUserId;

    @Schema(description = "遗留项清单", example = "现场指示灯异常，待后续处理")
    private String leftoverItems;

    @Schema(description = "遗留项状态 0无遗留 1待处理 2已闭环", example = "0")
    private Integer leftoverStatus;

    @Schema(description = "观察结论", example = "业务运行稳定，无异常")
    private String conclusion;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "观察期 24 小时")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
