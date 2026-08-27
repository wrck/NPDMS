package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import org.apache.commons.lang3.StringUtils;

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
        validateProcessReference(content, failures);
        Set<String> stageCodes = validateStages(content.getStages(), failures);
        Set<String> taskCodes = validateTasks(content.getTasks(), stageCodes, failures);
        validateMilestones(content.getMilestones(), stageCodes, failures);
        Set<String> deliverableCodes = validateDeliverables(content.getDeliverables(), stageCodes, taskCodes, failures);
        validateGates(content.getGates(), stageCodes, taskCodes, deliverableCodes, failures);
        validateTaskGateRefs(content.getTasks(), content.getGates(), failures);
        validatePreparationBindings(content.getTasks(), fixedFormCatalogJson,
                approvedPreparationItemCodes, failures);
        validateRequirementAnalysisBindings(content.getTasks(), failures);
        return failures;
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
        if (StringUtils.isBlank(content.getProcessDefinitionKey())) {
            failures.add("流程定义引用缺失：模板级流程定义ID为空");
        }
        if (StringUtils.isBlank(content.getProcessDefinitionVersion())) {
            failures.add("流程定义引用缺失：流程定义版本为空");
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
        return taskCodes;
    }

    private static void validateMilestones(List<TemplateDefinitionContent.MilestoneDef> milestones,
                                           Set<String> stageCodes, List<String> failures) {
        if (milestones == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
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
                                      Set<String> taskCodes, Set<String> deliverableCodes, List<String> failures) {
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
            validateGateReferences(gate, taskCodes, deliverableCodes, failures);
        }
    }

    private static void validateGateReferences(TemplateDefinitionContent.GateDef gate, Set<String> taskCodes,
                                               Set<String> deliverableCodes, List<String> failures) {
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
                case TemplateDefinitionContent.REF_TYPE_DELIVERABLE:
                    if (!deliverableCodes.contains(ref.getRefCode())) {
                        failures.add("门禁【" + gate.getGateCode() + "】引用的交付件【" + ref.getRefCode() + "】不存在");
                    }
                    break;
                case TemplateDefinitionContent.REF_TYPE_STATE:
                    // 状态码引用平台状态集合，发布时仅要求非空
                    break;
                case TemplateDefinitionContent.REF_TYPE_PROCESS:
                    if (StringUtils.isBlank(ref.getRefVersion())) {
                        failures.add("门禁【" + gate.getGateCode() + "】的流程引用【" + ref.getRefCode() + "】缺少版本");
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
