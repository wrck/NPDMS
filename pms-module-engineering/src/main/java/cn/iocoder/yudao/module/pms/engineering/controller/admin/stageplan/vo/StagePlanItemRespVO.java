package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理后台 - 阶段施工计划明细 Response VO（PLN-01/04）。
 */
@Schema(description = "管理后台 - 阶段施工计划明细 Response VO")
@Data
public class StagePlanItemRespVO {

    @Schema(description = "明细编号", example = "2048")
    private Long id;

    @Schema(description = "项目阶段ID", example = "3010")
    private Long phaseId;

    @Schema(description = "阶段编码快照", example = "PH-HW")
    private String phaseCode;

    @Schema(description = "阶段名称快照", example = "硬件实施")
    private String phaseName;

    @Schema(description = "阶段顺序", example = "3")
    private Integer sort;

    @Schema(description = "建议开始时间快照")
    private LocalDate suggestedStart;

    @Schema(description = "建议结束时间快照")
    private LocalDate suggestedEnd;

    @Schema(description = "计划开始时间")
    private LocalDate planStart;

    @Schema(description = "计划结束时间")
    private LocalDate planEnd;

    @Schema(description = "备注")
    private String remark;
}
