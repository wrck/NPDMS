package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目实例视图 Response VO（阶段→任务/里程碑/交付件/门禁+门禁引用行，按冻结版本只读）
 */
@Schema(description = "管理后台 - 项目实例视图 Response VO")
@Data
public class ProjectInstancesRespVO {

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "冻结的生命周期模板ID")
    private Long lifecycleTemplateId;

    @Schema(description = "冻结的模板版本号")
    private Integer lifecycleTemplateRevisionNo;

    @Schema(description = "阶段实例清单")
    private List<StageItem> stages = new ArrayList<>();

    @Schema(description = "任务实例清单")
    private List<TaskItem> tasks = new ArrayList<>();

    @Schema(description = "里程碑实例清单")
    private List<MilestoneItem> milestones = new ArrayList<>();

    @Schema(description = "交付件实例清单")
    private List<DeliverableItem> deliverables = new ArrayList<>();

    @Schema(description = "门禁实例清单（含结构化引用行）")
    private List<GateItem> gates = new ArrayList<>();

    @Data
    public static class StageItem {
        @Schema(description = "阶段码（S0～S6）")
        private String stageCode;
        @Schema(description = "阶段名称（快照）")
        private String name;
        @Schema(description = "阶段顺序")
        private Integer sortOrder;
        @Schema(description = "准入条件说明（快照）")
        private String entryCriteria;
        @Schema(description = "准出条件说明（快照）")
        private String exitCriteria;
        @Schema(description = "阶段实例状态")
        private String status;
    }

    @Data
    public static class TaskItem {
        @Schema(description = "任务码（项目内唯一）")
        private String taskCode;
        @Schema(description = "任务名称（快照）")
        private String name;
        @Schema(description = "父任务码（NULL=顶层）")
        private String parentTaskCode;
        @Schema(description = "所属阶段码")
        private String stageCode;
        @Schema(description = "优先级")
        private Integer priority;
        @Schema(description = "排序")
        private Integer sortOrder;
        @Schema(description = "预估工时（快照）")
        private java.math.BigDecimal estimatedHours;
        @Schema(description = "满意度适用时点（快照）")
        private String satisfactionTiming;
        @Schema(description = "任务实例状态（初始待分配）")
        private String status;
    }

    @Data
    public static class MilestoneItem {
        @Schema(description = "里程碑码")
        private String milestoneCode;
        @Schema(description = "里程碑名称（快照）")
        private String name;
        @Schema(description = "所属阶段码")
        private String stageCode;
        @Schema(description = "时点说明（快照）")
        private String timing;
        @Schema(description = "达成标准（快照）")
        private String criteria;
        @Schema(description = "里程碑状态")
        private String status;
    }

    @Data
    public static class DeliverableItem {
        @Schema(description = "交付件码")
        private String deliverableCode;
        @Schema(description = "交付件名称（快照）")
        private String name;
        @Schema(description = "所属阶段码")
        private String stageCode;
        @Schema(description = "关联任务码（NULL=阶段级）")
        private String taskCode;
        @Schema(description = "必需标志（快照）")
        private Boolean required;
        @Schema(description = "交付件状态")
        private String status;
    }

    @Data
    public static class GateItem {
        @Schema(description = "门禁码")
        private String gateCode;
        @Schema(description = "门禁名称（快照）")
        private String name;
        @Schema(description = "类型：ENTRY准入/EXIT准出")
        private String gateType;
        @Schema(description = "所属阶段码")
        private String stageCode;
        @Schema(description = "冻结的校验内容摘要")
        private String validationSummary;
        @Schema(description = "门禁状态")
        private String status;
        @Schema(description = "结构化引用行（TASK/DELIVERABLE/STATE/PROCESS）")
        private List<GateReferenceItem> references = new ArrayList<>();
    }

    @Data
    public static class GateReferenceItem {
        @Schema(description = "引用类型：TASK/DELIVERABLE/STATE/PROCESS")
        private String refType;
        @Schema(description = "引用编码（冻结）")
        private String refCode;
        @Schema(description = "引用版本（流程引用时使用）")
        private String refVersion;
    }
}
