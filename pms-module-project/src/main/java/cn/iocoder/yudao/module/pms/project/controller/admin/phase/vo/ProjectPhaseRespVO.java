package cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 项目阶段 Response VO（FR-PROJ-017 / FR-PROJ-019）。
 */
@Schema(description = "管理后台 - 项目阶段 Response VO")
@Data
public class ProjectPhaseRespVO {

    @Schema(description = "阶段编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "来源阶段模板编号", example = "10")
    private Long templateId;

    @Schema(description = "阶段名称", example = "需求调研")
    private String name;

    @Schema(description = "阶段编码", example = "PH-REQ")
    private String code;

    @Schema(description = "排序号", example = "0")
    private Integer sort;

    @Schema(description = "状态：0 未开始 1 进行中 2 已完成 3 已跳过", example = "0")
    private Integer status;

    @Schema(description = "建议开始时间")
    private LocalDateTime suggestedStartTime;

    @Schema(description = "建议结束时间")
    private LocalDateTime suggestedEndTime;

    @Schema(description = "计划开始时间")
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始时间")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间")
    private LocalDateTime actualEndTime;

    @Schema(description = "偏差原因", example = "需求变更导致延期")
    private String deviationReason;

    @Schema(description = "准入条件", example = "项目立项完成")
    private String entryCriteria;

    @Schema(description = "退出条件", example = "需求文档已评审通过")
    private String exitCriteria;

    @Schema(description = "负责角色编码", example = "PROJECT_MANAGER")
    private String responsibleRole;

    @Schema(description = "负责用户编号", example = "1")
    private Long responsibleUserId;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
