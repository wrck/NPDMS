package cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 项目阶段新增/修改 Request VO（FR-PROJ-017）。
 */
@Schema(description = "管理后台 - 项目阶段新增/修改 Request VO")
@Data
public class ProjectPhaseSaveReqVO {

    @Schema(description = "阶段编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "来源阶段模板编号", example = "10")
    private Long templateId;

    @Schema(description = "阶段名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求调研")
    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 128, message = "阶段名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "阶段编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "PH-REQ")
    @NotBlank(message = "阶段编码不能为空")
    @Size(max = 64, message = "阶段编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "排序号", example = "0")
    private Integer sort;

    @Schema(description = "状态：0 未开始 1 进行中 2 已完成 3 已跳过", example = "0")
    private Integer status;

    @Schema(description = "建议开始时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime suggestedStartTime;

    @Schema(description = "建议结束时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime suggestedEndTime;

    @Schema(description = "计划开始时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime actualEndTime;

    @Schema(description = "偏差原因", example = "需求变更导致延期")
    @Size(max = 500, message = "偏差原因长度不能超过 500 个字符")
    private String deviationReason;

    @Schema(description = "准入条件", example = "项目立项完成")
    @Size(max = 500, message = "准入条件长度不能超过 500 个字符")
    private String entryCriteria;

    @Schema(description = "退出条件", example = "需求文档已评审通过")
    @Size(max = 500, message = "退出条件长度不能超过 500 个字符")
    private String exitCriteria;

    @Schema(description = "负责角色编码", example = "PROJECT_MANAGER")
    @Size(max = 64, message = "负责角色编码长度不能超过 64 个字符")
    private String responsibleRole;

    @Schema(description = "负责用户编号", example = "1")
    private Long responsibleUserId;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
