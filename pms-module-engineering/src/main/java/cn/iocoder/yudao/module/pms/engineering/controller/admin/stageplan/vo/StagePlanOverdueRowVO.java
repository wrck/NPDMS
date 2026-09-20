package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 超期阶段行（PLN-03）：生效计划版本中未完成阶段超过计划完成时间的计算结果，不允许人工修改。
 */
@Schema(description = "管理后台 - 阶段施工计划超期阶段行")
@Data
public class StagePlanOverdueRowVO {

    @Schema(description = "项目编号", example = "7")
    private Long projectId;

    @Schema(description = "计划批次编号", example = "100")
    private Long batchId;

    @Schema(description = "阶段编号", example = "11")
    private Long phaseId;

    @Schema(description = "阶段名称")
    private String phaseName;

    @Schema(description = "计划完成时间（生效版本）")
    private LocalDate planEnd;

    @Schema(description = "超期天数（按日历日）", example = "5")
    private Long overdueDays;

    @Schema(description = "阶段状态：0 未开始 1 进行中")
    private Integer phaseStatus;

}
