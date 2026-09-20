package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台 - 阶段施工计划明细调整 Request VO（PLN-01/04，Demo 3.1 计划时间编辑）。
 */
@Schema(description = "管理后台 - 阶段施工计划明细调整 Request VO")
@Data
public class StagePlanItemUpdateReqVO {

    @Schema(description = "批次编号", example = "1024")
    @NotNull(message = "批次编号不能为空")
    private Long id;

    @Schema(description = "乐观锁版本号", example = "2")
    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Schema(description = "阶段计划明细")
    @NotEmpty(message = "阶段计划明细不能为空")
    private List<Item> items;

    @Schema(description = "备注")
    private String remark;
    private java.util.List<cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi.TaskPlan> tasks;

    @Schema(description = "阶段计划明细行")
    @Data
    public static class Item {

        @Schema(description = "明细编号", example = "2048")
        @NotNull(message = "明细编号不能为空")
        private Long id;

        @Schema(description = "计划开始时间")
        @NotNull(message = "计划开始时间不能为空")
        private LocalDate planStart;

        @Schema(description = "计划结束时间")
        @NotNull(message = "计划结束时间不能为空")
        private LocalDate planEnd;

        @Schema(description = "备注")
        private String remark;
    }
}
