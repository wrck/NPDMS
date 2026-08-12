package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 项目任务创建/修改 Request VO")
@Data
public class ProjectTaskSaveReqVO {

    @Schema(description = "任务编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "父任务编号，根任务为空", example = "1")
    private Long parentId;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求调研")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "任务编码，项目内唯一", example = "TASK-001")
    @Size(max = 64, message = "任务编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "任务描述", example = "完成需求调研并产出文档")
    @Size(max = 500, message = "任务描述长度不能超过 500 个字符")
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

    @Schema(description = "同级排序号", example = "0")
    private Integer sort;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
