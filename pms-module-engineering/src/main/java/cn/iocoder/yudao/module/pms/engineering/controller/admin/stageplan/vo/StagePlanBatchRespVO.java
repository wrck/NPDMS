package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 阶段施工计划批次 Response VO（PLN-01/04）。
 */
@Schema(description = "管理后台 - 阶段施工计划批次 Response VO")
@Data
public class StagePlanBatchRespVO {

    @Schema(description = "批次编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "状态：0草稿 1审批中 2已生效 3已驳回")
    private Integer status;

    @Schema(description = "推算基准工期版本ID（空=按阶段建议时间）")
    private Long durationRevisionId;
    private java.time.LocalDate calculatedStart;
    private java.time.LocalDate calculatedEnd;
    private String inputSnapshot;


    @Schema(description = "基准工期开始日期（已生效工期基线；空=未录入工期）")
    private LocalDate baselineStart;

    @Schema(description = "基准工期结束日期")
    private LocalDate baselineEnd;

    @Schema(description = "备注")
    private String remark;
    private java.util.List<cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi.TaskPlan> tasks;

    @Schema(description = "审批流程实例ID")
    private String bpmProcessInstanceId;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "审批生效时间")
    private LocalDateTime effectiveAt;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "阶段计划明细（按阶段顺序）")
    private List<StagePlanItemRespVO> items;
}
