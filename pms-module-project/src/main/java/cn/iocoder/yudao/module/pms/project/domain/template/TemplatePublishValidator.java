package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import org.apache.commons.lang3.StringUtils;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模板发布校验器（BR-2 / PM-03 规则2）
 * <p>
 * 版本必须完整定义：适用条件、阶段顺序、阶段任务、里程碑、交付件、准入/准出门禁和
 * 流程定义版本引用；引用对象不存在不得发布。校验失败返回具体失败项清单（保持草稿）。
 */
public final class TemplatePublishValidator {

    /** 阶段码格式：S0～S6（PRD 4.2.3 生命周期阶段） */
    private static final String STAGE_CODE_PATTERN = "S[0-6]";
    private static final TaskExecutionContractFactory EXECUTION_CONTRACT_FACTORY =
            new TaskExecutionContractFactory();

    private TemplatePublishValidator() {
    }

    /**
     * 校验模板定义内容完整性；返回失败项清单，空列表表示可发布。
     */
    public static List<String> validate(TemplateDefinitionContent content) {
        return validate(content, null);
    }

    /**
     * 校验模板及PRE-02固定目录引用；fixedFormCatalogJson仅在模板声明PRE-02时使用。
     */
    public static List<String> validate(TemplateDefinitionContent content, String fixedFormCatalogJson) {
        return validate(content, fixedFormCatalogJson, null);
    }

    public static List<String> validate(TemplateDefinitionContent content, String fixedFormCatalogJson,
                                        Set<String> approvedPreparationItemCodes) {
        List<String> failures = new ArrayList<>();
        if (content == null) {
            failures.add("模板内容为空");
            return failures;
        }
        failures.addAll(validateClosurePolicy(content.getClosurePolicy()));
        validateProcessReference(content, failures);
        StageTransitionGraph graph = new StageTransitionGraph(
                content.getStages() == null ? null : content.getStages().stream().map(stage -> stage == null ? null
                        : new StageTransitionGraph.Stage(stage.getStageCode(), stage.getStart(), stage.getTerminal())).toList(),
                content.getTransitions() == null ? null : content.getTransitions().stream().map(edge -> edge == null ? null
                        : new StageTransitionDefinition(edge.getTransitionCode(), edge.getFromStageCode(), edge.getToStageCode(),
                        edge.getConditionRuleRevisionId(), edge.getPriority(), edge.getDefaultBranch())).toList());
        StageTransitionGraphValidator.validate(graph).forEach(issue -> failures.add(issue.path() + ": " + issue.message()));
        if (content.getTransitions() != null) for (var edge : content.getTransitions()) {
            if (edge != null && (edge.getRevisionNo() == null || edge.getRevisionNo() <= 0)) failures.add("转移关系版本必须为正数");
        }
        Set<String> stageCodes = validateStages(content.getStages(), failures);
        Set<String> taskCodes = validateTasks(content.getTasks(), stageCodes, failures);
        Set<String> milestoneCodes = validateMilestones(content.getMilestones(), stageCodes, failures);
        Set<String> deliverableCodes = validateDeliverables(content.getDeliverables(), stageCodes, taskCodes, failures);
        validateGates(content.getGates(), stageCodes, taskCodes, milestoneCodes, deliverableCodes, failures);
        validateTaskGateRefs(content.getTasks(), content.getGates(), failures);
        validatePreparationBindings(content.getTasks(), fixedFormCatalogJson,
                approvedPreparationItemCodes, failures);
        validateRequirementAnalysisBindings(content.getTasks(), failures);
        return failures;
    }

    /** 仅检查可选闭环schema；真实BPM及显式材料审核资格由发布服务向Owner重验。 */
    public static List<String> validateClosurePolicy(TemplateDefinitionContent.ClosurePolicy policy) {
        if (policy == null) {
            return List.of();
        }
        try {
            new TemplateDefinitionContent.ClosurePolicy(policy.toJson());
            return List.of();
        } catch (IllegalArgumentException ex) {
            return List.of(ex.getMessage());
        }
    }

    public static boolean requiresPreparationCatalog(TemplateDefinitionContent content) {
        return content != null && content.getTasks() != null && content.getTasks().stream()
                .anyMatch(task -> task != null
                        && PreparationWorkBindingSchema.TARGET_OBJECT_KEY.equals(task.getTargetObjectKey()));
    }

    public static boolean requiresRequirementAnalysisBinding(TemplateDefinitionContent content) {
        return content != null && content.getTasks() != null && content.getTasks().stream()
                .anyMatch(task -> task != null
                        && RequirementAnalysisWorkBindingSchema.TARGET_OBJECT_KEY.equals(task.getTargetObjectKey()));
    }

    private static void validateProcessReference(TemplateDefinitionContent content, List<String> failures) {
        if (StringUtils.isNotBlank(content.getProcessDefinitionVersion())) {
            failures.add("流程定义引用无效：不得写入PMS流程版本，实例以Flowable流程定义ID冻结版本");
        }
    }

    private static Set<String> validateStages(List<TemplateDefinitionContent.StageDef> stages, List<String> failures) {
        Set<String> stageCodes = new HashSet<>();
        if (stages == null || stages.isEmpty()) {
            failures.add("阶段定义缺失：至少需要一个阶段（S0～S6）");
            return stageCodes;
        }
        for (TemplateDefinitionContent.StageDef stage : stages) {
            if (stage == null || StringUtils.isBlank(stage.getStageCode())) {
                failures.add("阶段编码不能为空");
                continue;
            }
            if (!stage.getStageCode().matches(STAGE_CODE_PATTERN)) {
                failures.add("阶段编码【" + stage.getStageCode() + "】须为 S0～S6");
                continue;
            }
            if (!stageCodes.add(stage.getStageCode())) {
                failures.add("阶段编码【" + stage.getStageCode() + "】重复");
                continue;
            }
            if (stage.getDefinitionRevisionId() == null || stage.getDefinitionRevisionId() <= 0
                    || stage.getWorkBindingRevisionId() == null || stage.getWorkBindingRevisionId() <= 0
                    || stage.getPermissionPolicyRevisionId() == null || stage.getPermissionPolicyRevisionId() <= 0
                    || stage.getCompletionRuleRevisionId() == null || stage.getCompletionRuleRevisionId() <= 0) {
                failures.add("阶段【" + stage.getStageCode() + "】精确定义、绑定、权限或完成规则引用缺失");
            }
            if (StringUtils.isBlank(stage.getName())) {
                failures.add("阶段【" + stage.getStageCode() + "】名称为空");
            }
            if (stage.getSortOrder() == null || stage.getSortOrder() < 0) {
                failures.add("阶段【" + stage.getStageCode() + "】顺序无效");
            }
        }
        return stageCodes;
    }

    private static Set<String> validateTasks(List<TemplateDefinitionContent.TaskDef> tasks,
                                             Set<String> stageCodes, List<String> failures) {
        Set<String> taskCodes = new HashSet<>();
        if (tasks != null) {
            for (TemplateDefinitionContent.TaskDef task : tasks) {
                if (task == null || StringUtils.isBlank(task.getTaskCode())) {
                    failures.add("任务编码不能为空");
                    continue;
                }
                if (!taskCodes.add(task.getTaskCode())) {
                    failures.add("任务编码【" + task.getTaskCode() + "】重复");
                    continue;
                }
                if (StringUtils.isBlank(task.getName())) {
                    failures.add("任务【" + task.getTaskCode() + "】名称为空");
                }
                if (!stageCodes.contains(task.getStageCode())) {
                    failures.add("任务【" + task.getTaskCode() + "】引用的阶段【" + task.getStageCode() + "】不存在");
                }
                if ("S0".equals(task.getStageCode())) {
                    failures.add("任务【" + task.getTaskCode() + "】属于S0项目基本操作，不应重复配置为交付任务");
                }
                if (task.getWorkBindingTypeCode() != null
                        && !"TASK_NATIVE".equals(task.getWorkBindingTypeCode())
                        && "TASK_NATIVE_STATUS".equals(task.getCompletionRuleTypeCode())) {
                    failures.add("任务【" + task.getTaskCode() + "】已绑定业务页面或表单，须配置真实业务完成依据，不能沿用原生手工完成");
                }
                try {
                    EXECUTION_CONTRACT_FACTORY.validateDefinition(task);
                } catch (IllegalArgumentException ex) {
                    failures.add("任务【" + task.getTaskCode() + "】" + ex.getMessage());
                }
                String parent = task.getParentTaskCode();
                if (parent != null) {
                    if (parent.equals(task.getTaskCode())) {
                        failures.add("任务【" + task.getTaskCode() + "】不能以自身为父任务");
                    }
                }
            }
            // 二次遍历校验父任务引用存在（允许前向引用）
            for (TemplateDefinitionContent.TaskDef task : tasks) {
                if (task == null || task.getParentTaskCode() == null) {
                    continue;
                }
                if (!taskCodes.contains(task.getParentTaskCode())) {
                    failures.add("任务【" + task.getTaskCode() + "】的父任务【" + task.getParentTaskCode() + "】不存在");
                }
            }
        }
        if (tasks != null) {
            java.util.Map<String, TemplateDefinitionContent.TaskDef> byCode = new java.util.HashMap<>();
            for (var task : tasks) if (task != null && task.getTaskCode() != null) byCode.put(task.getTaskCode(), task);
            for (var task : tasks) {
                if (task == null) continue;
                Set<String> path = new HashSet<>(); var current = task;
                while (current != null) {
                    if (!path.add(current.getTaskCode())) { failures.add("任务父子关系构成循环：" + task.getTaskCode()); break; }
                    current = byCode.get(current.getParentTaskCode());
                }
            }
        }
        return taskCodes;
    }

    private static Set<String> validateMilestones(List<TemplateDefinitionContent.MilestoneDef> milestones,
                                                  Set<String> stageCodes, List<String> failures) {
        Set<String> codes = new HashSet<>();
        if (milestones == null) {
            return codes;
        }
        for (TemplateDefinitionContent.MilestoneDef milestone : milestones) {
            if (milestone == null || StringUtils.isBlank(milestone.getMilestoneCode())) {
                failures.add("里程碑编码不能为空");
                continue;
            }
            if (!codes.add(milestone.getMilestoneCode())) {
                failures.add("里程碑编码【" + milestone.getMilestoneCode() + "】重复");
                continue;
            }
            if (!stageCodes.contains(milestone.getStageCode())) {
                failures.add("里程碑【" + milestone.getMilestoneCode() + "】引用的阶段【"
                        + milestone.getStageCode() + "】不存在");
            }
        }
        return codes;
    }

    private static Set<String> validateDeliverables(List<TemplateDefinitionContent.DeliverableDef> deliverables,
                                                    Set<String> stageCodes, Set<String> taskCodes,
                                                    List<String> failures) {
        Set<String> deliverableCodes = new HashSet<>();
        if (deliverables == null) {
            return deliverableCodes;
        }
        for (TemplateDefinitionContent.DeliverableDef deliverable : deliverables) {
            if (deliverable == null || StringUtils.isBlank(deliverable.getDeliverableCode())) {
                failures.add("交付件编码不能为空");
                continue;
            }
            if (!deliverableCodes.add(deliverable.getDeliverableCode())) {
                failures.add("交付件编码【" + deliverable.getDeliverableCode() + "】重复");
                continue;
            }
            if (!stageCodes.contains(deliverable.getStageCode())) {
                failures.add("交付件【" + deliverable.getDeliverableCode() + "】引用的阶段【"
                        + deliverable.getStageCode() + "】不存在");
            }
            if (deliverable.getTaskCode() != null && !taskCodes.contains(deliverable.getTaskCode())) {
                failures.add("交付件【" + deliverable.getDeliverableCode() + "】引用的任务【"
                        + deliverable.getTaskCode() + "】不存在");
            }
        }
        return deliverableCodes;
    }

    private static void validateGates(List<TemplateDefinitionContent.GateDef> gates, Set<String> stageCodes,
                                      Set<String> taskCodes, Set<String> milestoneCodes,
                                      Set<String> deliverableCodes, List<String> failures) {
        if (gates == null) {
            return;
        }
        Set<String> gateCodes = new HashSet<>();
        for (TemplateDefinitionContent.GateDef gate : gates) {
            if (gate == null || StringUtils.isBlank(gate.getGateCode())) {
                failures.add("门禁编码不能为空");
                continue;
            }
            if (!gateCodes.add(gate.getGateCode())) {
                failures.add("门禁编码【" + gate.getGateCode() + "】重复");
                continue;
            }
            if (!TemplateDefinitionContent.GATE_TYPE_ENTRY.equals(gate.getGateType())
                    && !TemplateDefinitionContent.GATE_TYPE_EXIT.equals(gate.getGateType())) {
                failures.add("门禁【" + gate.getGateCode() + "】类型须为 ENTRY 或 EXIT");
            }
            if (!stageCodes.contains(gate.getStageCode())) {
                failures.add("门禁【" + gate.getGateCode() + "】引用的阶段【" + gate.getStageCode() + "】不存在");
            }
            validateGateReferences(gate, taskCodes, milestoneCodes, deliverableCodes, failures);
        }
    }

    private static void validateGateReferences(TemplateDefinitionContent.GateDef gate, Set<String> taskCodes,
                                               Set<String> milestoneCodes, Set<String> deliverableCodes,
                                               List<String> failures) {
        List<TemplateDefinitionContent.GateRef> references = gate.getReferences();
        if (references == null || references.isEmpty()) {
            failures.add("门禁【" + gate.getGateCode() + "】缺少引用行（任务/交付件/状态/流程）");
            return;
        }
        for (TemplateDefinitionContent.GateRef ref : references) {
            if (ref == null || StringUtils.isBlank(ref.getRefCode())) {
                failures.add("门禁【" + gate.getGateCode() + "】存在引用编码为空的引用行");
                continue;
            }
            switch (ref.getRefType() == null ? "" : ref.getRefType()) {
                case TemplateDefinitionContent.REF_TYPE_TASK:
                    if (!taskCodes.contains(ref.getRefCode())) {
                        failures.add("门禁【" + gate.getGateCode() + "】引用的任务【" + ref.getRefCode() + "】不存在");
                    }
                    break;
                case TemplateDefinitionContent.REF_TYPE_MILESTONE:
                    if (!milestoneCodes.contains(ref.getRefCode())) {
                        failures.add("门禁【" + gate.getGateCode() + "】引用的里程碑【"
                                + ref.getRefCode() + "】不存在");
                    }
                    break;
                case TemplateDefinitionContent.REF_TYPE_DELIVERABLE:
                    if (!deliverableCodes.contains(ref.getRefCode())) {
                        failures.add("门禁【" + gate.getGateCode() + "】引用的交付件【" + ref.getRefCode() + "】不存在");
                    }
                    break;
                case TemplateDefinitionContent.REF_TYPE_STATE:
                    // 状态码引用平台状态集合，发布时仅要求非空
                    break;
                case TemplateDefinitionContent.REF_TYPE_PROCESS:
                case TemplateDefinitionContent.REF_TYPE_APPROVAL:
                    if (StringUtils.isNotBlank(ref.getRefVersion())) {
                        failures.add("门禁【" + gate.getGateCode() + "】的流程引用【" + ref.getRefCode()
                                + "】不得写入PMS流程版本");
                    }
                    break;
                default:
                    failures.add("门禁【" + gate.getGateCode() + "】存在未知类型的引用行【" + ref.getRefType() + "】");
                    break;
            }
        }
    }

    private static void validateTaskGateRefs(List<TemplateDefinitionContent.TaskDef> tasks,
                                             List<TemplateDefinitionContent.GateDef> gates,
                                             List<String> failures) {
        if (tasks == null) {
            return;
        }
        Set<String> gateCodes = new HashSet<>();
        if (gates != null) {
            gates.stream().filter(gate -> gate != null && StringUtils.isNotBlank(gate.getGateCode()))
                    .map(TemplateDefinitionContent.GateDef::getGateCode).forEach(gateCodes::add);
        }
        for (TemplateDefinitionContent.TaskDef task : tasks) {
            if (task != null && StringUtils.isNotBlank(task.getGateRef()) && !gateCodes.contains(task.getGateRef())) {
                failures.add("任务【" + task.getTaskCode() + "】GateRef【" + task.getGateRef() + "】不存在");
            }
        }
    }

    private static void validatePreparationBindings(List<TemplateDefinitionContent.TaskDef> tasks,
                                                    String fixedFormCatalogJson,
                                                    Set<String> approvedItemCodes,
                                                    List<String> failures) {
        if (tasks == null) {
            return;
        }
        int matches = 0;
        for (TemplateDefinitionContent.TaskDef task : tasks) {
            if (task == null
                    || !PreparationWorkBindingSchema.TARGET_OBJECT_KEY.equals(task.getTargetObjectKey())) {
                continue;
            }
            if (!PreparationWorkBindingSchema.isPreparationBinding(task)) {
                failures.add("任务【" + task.getTaskCode() + "】PRE-02目标四元组无效");
                continue;
            }
            matches++;
            try {
                if (approvedItemCodes == null) {
                    PreparationWorkBindingSchema.parseAndValidate(task.getBindingConfig(), fixedFormCatalogJson);
                } else {
                    PreparationWorkBindingSchema.parseAndValidate(task.getBindingConfig(), fixedFormCatalogJson,
                            approvedItemCodes);
                }
            } catch (IllegalArgumentException ex) {
                failures.add("任务【" + task.getTaskCode() + "】" + ex.getMessage());
            }
        }
        if (matches > 1) {
            failures.add("PRE-02 WorkBinding必须在模板版本内唯一");
        }
    }

    private static void validateRequirementAnalysisBindings(List<TemplateDefinitionContent.TaskDef> tasks,
                                                             List<String> failures) {
        if (tasks == null) {
            return;
        }
        int matches = 0;
        for (TemplateDefinitionContent.TaskDef task : tasks) {
            if (task == null || !RequirementAnalysisWorkBindingSchema.TARGET_OBJECT_KEY.equals(
                    task.getTargetObjectKey())) {
                continue;
            }
            if (!RequirementAnalysisWorkBindingSchema.isRequirementAnalysisBinding(task)) {
                failures.add("任务【" + task.getTaskCode() + "】PRE-04目标四元组无效");
                continue;
            }
            matches++;
            try {
                RequirementAnalysisWorkBindingSchema.parseFrozen(task.getBindingConfig());
            } catch (IllegalArgumentException ex) {
                failures.add("任务【" + task.getTaskCode() + "】" + ex.getMessage());
            }
        }
        if (matches > 1) {
            failures.add("PRE-04 WorkBinding必须在模板版本内唯一");
        }
    }
}
