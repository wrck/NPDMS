package cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - 工期倒排阶段明细 Response VO（FR-PROJ-018）。
 */
@Schema(description = "管理后台 - 工期倒排阶段明细 Response VO")
@Data
public class ScheduleBackwardItemRespVO {

    @Schema(description = "明细编号", example = "1")
    private Long id;

    @Schema(description = "倒排记录编号", example = "1024")
    private Long backwardId;

    @Schema(description = "项目阶段编号", example = "10")
    private Long phaseId;

    @Schema(description = "阶段名称", example = "需求调研")
    private String phaseName;

    @Schema(description = "计划开始日期", example = "2026-10-01")
    private LocalDate plannedStartDate;

    @Schema(description = "计划结束日期", example = "2026-10-07")
    private LocalDate plannedEndDate;

    @Schema(description = "建议最晚日期", example = "2026-10-10")
    private LocalDate recommendedLatestDate;

    @Schema(description = "是否存在冲突", example = "false")
    private Boolean hasConflict;

    @Schema(description = "冲突原因", example = "计划开始日期早于今天")
    private String conflictReason;

    @Schema(description = "阶段排序", example = "1")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
