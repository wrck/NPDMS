package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手工创建项目 Response VO（创建结果与实例化摘要，幂等重放原样返回）
 */
@Schema(description = "管理后台 - 项目手工创建 Response VO")
@Data
public class ProjectCreateRespVO {

    @Schema(description = "项目ID", example = "1")
    private Long id;

    @Schema(description = "项目编码（租户内唯一，创建后不可变）", example = "PJT2026000001")
    private String projectCode;

    @Schema(description = "项目状态（初始 S0）", example = "S0")
    private String status;

    @Schema(description = "生命周期状态", example = "ACTIVE")
    private String lifecycleStatus;

    @Schema(description = "当前阶段", example = "S0")
    private String currentStage;

    @Schema(description = "主责指派状态", example = "UNASSIGNED")
    private String assignmentStatus;

    @Schema(description = "项目版本", example = "0")
    private Integer version;

    @Schema(description = "冻结的生命周期模板ID", example = "910001")
    private Long lifecycleTemplateId;

    @Schema(description = "冻结的模板版本号", example = "2")
    private Integer lifecycleTemplateRevisionNo;

    @Schema(description = "模板加载方式：AUTO_DEFAULT/MANUAL_SELECTED", example = "AUTO_DEFAULT")
    private String templateLoadMethod;

    @Schema(description = "实例化阶段数", example = "7")
    private Integer stageCount;

    @Schema(description = "实例化任务数", example = "24")
    private Integer taskCount;

    @Schema(description = "实例化里程碑数", example = "8")
    private Integer milestoneCount;

    @Schema(description = "实例化交付件数", example = "17")
    private Integer deliverableCount;

    @Schema(description = "实例化门禁数", example = "14")
    private Integer gateCount;

    @Schema(description = "是否已同步指派一级服务经理", example = "true")
    private Boolean serviceManagerAssigned;

    @Schema(description = "模板候选结果：UNIQUE/MULTIPLE_MATCHES", example = "UNIQUE")
    private String matchResult;

    @Schema(description = "首次选模方式：AUTO_UNIQUE/EXPLICIT_SELECTION", example = "AUTO_UNIQUE")
    private String matchDecisionMode;

    @Schema(description = "首次模板匹配决策稳定操作ID")
    private String matchOperationId;
}
