package cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 计划变更阶段快照 Response VO")
@Data
public class PlanChangePhaseSnapshotRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "变更申请编号")
    private Long changeRequestId;

    @Schema(description = "项目阶段编号")
    private Long phaseId;

    @Schema(description = "阶段名称")
    private String phaseName;

    @Schema(description = "变更前计划开始时间")
    private LocalDateTime beforePlanStart;

    @Schema(description = "变更前计划结束时间")
    private LocalDateTime beforePlanEnd;

    @Schema(description = "变更后计划开始时间")
    private LocalDateTime afterPlanStart;

    @Schema(description = "变更后计划结束时间")
    private LocalDateTime afterPlanEnd;

    @Schema(description = "阶段变更说明")
    private String changeRemark;

}
