package cn.iocoder.yudao.module.pms.project.domain.template;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目模板定义内容（草稿编辑与发布校验的领域载体）
 * <p>
 * 对应 V52 版本表四维条件 + 六类结构化定义行；发布校验（BR-2）据此逐项检查，
 * 不使用 JSON 承载唯一/过滤/门禁核心字段（09 原则7）。
 */
@Data
public class TemplateDefinitionContent {

    /** 门禁类型：准入 */
    public static final String GATE_TYPE_ENTRY = "ENTRY";
    /** 门禁类型：准出 */
    public static final String GATE_TYPE_EXIT = "EXIT";

    /** 门禁引用类型：任务 */
    public static final String REF_TYPE_TASK = "TASK";
    /** 门禁引用类型：交付件 */
    public static final String REF_TYPE_DELIVERABLE = "DELIVERABLE";
    /** 门禁引用类型：状态码 */
    public static final String REF_TYPE_STATE = "STATE";
    /** 门禁引用类型：流程 */
    public static final String REF_TYPE_PROCESS = "PROCESS";

    /** 匹配条件：签约方式（字典 pms_signing_method，null=不限） */
    private String signingMethod;
    /** 匹配条件：项目类别（字典 pms_project_category，null=不限） */
    private String projectCategory;
    /** 匹配条件：实施方式（字典 pms_implementation_method，null=不限） */
    private String implementationMethod;
    /** 匹配条件：重大项目级别（CRM 来源属性映射，null=不限） */
    private String majorProjectLevel;

    /** 模板级流程定义引用（仅存引用，发布要求非空，不校验流程内部） */
    private String processDefinitionKey;
    /** 流程定义版本引用 */
    private String processDefinitionVersion;

    /** 阶段定义（S0～S6，顺序） */
    private List<StageDef> stages = new ArrayList<>();
    /** 任务定义（版本内唯一，可父子的 WBS 初始化清单） */
    private List<TaskDef> tasks = new ArrayList<>();
    /** 里程碑定义 */
    private List<MilestoneDef> milestones = new ArrayList<>();
    /** 交付件定义 */
    private List<DeliverableDef> deliverables = new ArrayList<>();
    /** 门禁定义（含结构化引用行） */
    private List<GateDef> gates = new ArrayList<>();

    @Data
    public static class StageDef {
        /** 阶段码 S0～S6 */
        private String stageCode;
        private String name;
        /** 阶段顺序 */
        private Integer sortOrder;
        /** 准入条件说明 */
        private String entryCriteria;
        /** 准出条件说明 */
        private String exitCriteria;
    }

    @Data
    public static class TaskDef {
        /** 任务码（版本内唯一） */
        private String taskCode;
        private String name;
        /** 父任务码（null=顶层） */
        private String parentTaskCode;
        /** 所属阶段码 */
        private String stageCode;
        /** 优先级 */
        private Integer priority;
        /** 排序 */
        private Integer sortOrder;
        /** 预估工时 */
        private BigDecimal estimatedHours;
        /** 满意度适用时点（null=不适用，由 ACC-02 消费） */
        private String satisfactionTiming;
        /** 任务说明 */
        private String description;
    }

    @Data
    public static class MilestoneDef {
        /** 里程碑码（版本内唯一） */
        private String milestoneCode;
        private String name;
        /** 所属阶段码 */
        private String stageCode;
        /** 时点说明 */
        private String timing;
        /** 达成标准 */
        private String criteria;
    }

    @Data
    public static class DeliverableDef {
        /** 交付件码（版本内唯一） */
        private String deliverableCode;
        private String name;
        /** 所属阶段码 */
        private String stageCode;
        /** 关联任务码（null=阶段级） */
        private String taskCode;
        /** 必需标志 */
        private Boolean required;
    }

    @Data
    public static class GateDef {
        /** 门禁码（版本内唯一） */
        private String gateCode;
        private String name;
        /** 类型 ENTRY/EXIT */
        private String gateType;
        /** 所属阶段码 */
        private String stageCode;
        /** 门禁说明 */
        private String description;
        /** 结构化引用行（任务/交付件/状态/流程） */
        private List<GateRef> references = new ArrayList<>();
    }

    @Data
    public static class GateRef {
        /** 引用类型 TASK/DELIVERABLE/STATE/PROCESS */
        private String refType;
        /** 引用编码 */
        private String refCode;
        /** 引用版本（流程引用时使用） */
        private String refVersion;
    }
}
