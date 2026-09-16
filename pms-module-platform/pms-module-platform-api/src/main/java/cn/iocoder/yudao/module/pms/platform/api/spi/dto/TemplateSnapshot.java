package cn.iocoder.yudao.module.pms.platform.api.spi.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 模板内容快照（迁移自源工程 pms-common com.dp.plat.common.dto.TemplateSnapshot，
 * 深拷贝到项目时反序列化）。
 * 关联设计文档：§2.2 ProjectTemplateVersion
 */
@Data
public class TemplateSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 阶段定义 */
    private List<PhaseDef> phases;

    /** 任务定义（含父子层级） */
    private List<TaskDef> tasks;

    /** 里程碑定义 */
    private List<MilestoneDef> milestones;

    /** 交付件定义 */
    private List<DeliverableDef> deliverables;

    /** 任务依赖定义 */
    private List<DependencyDef> dependencies;

    /** 审批计划定义 */
    private List<ApprovalPlanDef> approvalPlans;

    /** 分配规则（自动分配任务给角色） */
    private List<AssigneeRule> assigneeRules;

    @Data
    public static class PhaseDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String phaseCode;
        private String phaseName;
        private Integer sortOrder;
        private PhaseCriteria entryCriteria;
        private PhaseExitGate exitCriteria;
    }

    @Data
    public static class TaskDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskName;
        private String taskType;
        private String parentTaskName; // 通过名称引用，便于跨模板复用
        private String phaseCode;      // 关联阶段
        private Integer plannedHours;
        private String priority;
        private Integer sortOrder;
    }

    @Data
    public static class MilestoneDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String milestoneName;
        private String milestoneType;
        private String phaseCode;
        private Integer sortOrder;
    }

    @Data
    public static class DeliverableDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deliverableName;
        private String deliverableType;
        private String phaseCode;
        private Boolean mandatory;
        private String approverRole;
        /**
         * 引用实体类型（仅当 deliverableType 为 ENTITY_REF 时使用）。
         * 取值参考字典 pms_deliverable_ref_entity_type：TASK/ASSET/PHASE/PROJECT/DELIVERABLE/REPORT。
         */
        private String refEntityType;
        /**
         * 引用实体 ID（仅当 deliverableType 为 ENTITY_REF 时使用）。
         * 模板态可为空（创建项目时由用户补选），项目态为数据库主键。
         */
        private Long refEntityId;
    }

    @Data
    public static class DependencyDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String predecessorTaskName;
        private String successorTaskName;
        private String dependencyType; // FS/FF/SS/SF
        private Integer lagDays;
    }

    @Data
    public static class ApprovalPlanDef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String approvalType;
        private String triggerPhaseCode;
        private List<String> approverRoles;
    }

    @Data
    public static class AssigneeRule implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNamePattern;
        private String role;
    }
}