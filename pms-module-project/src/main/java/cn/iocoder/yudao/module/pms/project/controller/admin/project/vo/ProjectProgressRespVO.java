package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 项目进度 Response VO（FR-PROJ-021 / T-V1-PROJ-009）。
 * <p>
 * 总体进度 = 60% 任务进度 + 40% 阶段进度。
 */
@Schema(description = "管理后台 - 项目进度 Response VO")
@Data
public class ProjectProgressRespVO {

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "阶段进度（已完成阶段数 / 阶段总数 * 100）", example = "40")
    private Integer phaseProgress;

    @Schema(description = "任务进度（已完成任务数 / 任务总数 * 100）", example = "50")
    private Integer taskProgress;

    @Schema(description = "总体进度（60% 任务 + 40% 阶段，四舍五入取整）", example = "46")
    private Integer overallProgress;

    @Schema(description = "阶段总数", example = "5")
    private Integer phaseTotalCount;

    @Schema(description = "已完成阶段数", example = "2")
    private Integer phaseCompletedCount;

    @Schema(description = "任务总数", example = "20")
    private Integer taskTotalCount;

    @Schema(description = "已完成任务数", example = "10")
    private Integer taskCompletedCount;
}
