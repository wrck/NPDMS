package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 项目任务 Response VO")
@Data
public class ProjectTaskRespVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long projectId;

    @Schema(description = "父任务编号", example = "1")
    private Long parentId;

    @Schema(description = "根任务编号", example = "1")
    private Long rootId;

    @Schema(description = "物化路径", example = "/1/1024/")
    private String path;

    @Schema(description = "路径深度", example = "0")
    private Integer depth;

    @Schema(description = "同级排序号", example = "0")
    private Integer sort;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求调研")
    private String name;

    @Schema(description = "任务编码", example = "TASK-001")
    private String code;

    @Schema(description = "任务描述", example = "完成需求调研并产出文档")
    private String description;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "执行人用户编号", example = "2")
    private Long assigneeUserId;

    @Schema(description = "状态：0草稿 1待处理 2进行中 3受阻 4待验证 5已完成 6已取消", example = "0")
    private Integer status;

    @Schema(description = "优先级", example = "0")
    private Integer priority;

    @Schema(description = "计划开始时间", example = "2024-01-01T00:00:00")
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间", example = "2024-01-31T00:00:00")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始时间", example = "2024-01-02T00:00:00")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间", example = "2024-01-30T00:00:00")
    private LocalDateTime actualEndTime;

    @Schema(description = "预估工时", example = "40.00")
    private BigDecimal estimatedHours;

    @Schema(description = "实际工时", example = "38.50")
    private BigDecimal actualHours;

    @Schema(description = "进度 0-100", example = "0")
    private Integer progress;

    @Schema(description = "聚合进度（子任务加权平均）", example = "30")
    private Integer aggregatedProgress;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
