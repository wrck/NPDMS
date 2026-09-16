package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionPayloadValidator;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** PM-03 V2 deterministic authoring -> execution compiler. */
@Component
public class TemplateCompiler {

    public static final String COMPILER_VERSION = "template-liteflow-2";
    private final cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler ruleCompiler =
            new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TemplateOperationCompilation operationCompilation;

    public record Compilation(TemplateExecutionSnapshot snapshot, String snapshotHash, List<Issue> issues) {
        public boolean valid() { return issues.isEmpty(); }
    }

    public Compilation compile(TemplateDesignerDocument source) {
        List<Issue> issues = new ArrayList<>();
        if (source == null) {
            issues.add(new Issue("designer", "REQUIRED", "模板设计文档不能为空"));
            return new Compilation(null, null, List.copyOf(issues));
        }
        try {
            source = cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.forCompilation(source);
        } catch (RuntimeException invalidRuleCollection) {
            issues.add(new Issue("rules", "INVALID_VERSION_RULES", invalidRuleCollection.getMessage()));
            return new Compilation(null, null, List.copyOf(issues));
        }
        if (!Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(source.getSchemaVersion())) {
            issues.add(new Issue("schemaVersion", "UNSUPPORTED_SCHEMA", "仅支持模板设计schema v2"));
        }
        requireCollections(source, issues);
        if (!issues.isEmpty()) return new Compilation(null, null, List.copyOf(issues));

        validateNodes(source, issues);
        validateGraph(source, issues);
        validateRules(source, issues);
        validateBindings(source, issues);
        validateGateReferences(source, issues);
        TemplateOperationCompilation.Result operations = TemplateOperationCompilation.Result.empty();
        if (TemplateOperationCompilation.hasContracts(source)) {
            if (operationCompilation == null) {
                issues.add(new Issue("operationContract", "OPERATION_CATALOG_UNAVAILABLE", "操作编译能力未装配"));
            } else {
                operations = operationCompilation.compile(source);
                issues.addAll(operations.issues());
            }
        }
        if (!issues.isEmpty()) return new Compilation(null, null, List.copyOf(issues));

        TemplateExecutionSnapshot snapshot = buildSnapshot(source);
        operations.install(snapshot);
        return new Compilation(snapshot, TemplateExecutionSnapshotHasher.hash(snapshot), List.of());
    }

    private void requireCollections(TemplateDesignerDocument source, List<Issue> issues) {
        if (source.getStages() == null) issues.add(new Issue("stages", "REQUIRED", "阶段集合不能为空引用"));
        if (source.getTasks() == null) issues.add(new Issue("tasks", "REQUIRED", "任务集合不能为空引用"));
        if (source.getTransitions() == null) issues.add(new Issue("transitions", "REQUIRED", "阶段关系集合不能为空引用"));
        if (source.getMilestones() == null) issues.add(new Issue("milestones", "REQUIRED", "里程碑集合不能为空引用"));
        if (source.getDeliverables() == null) issues.add(new Issue("deliverables", "REQUIRED", "交付件集合不能为空引用"));
        if (source.getGates() == null) issues.add(new Issue("gates", "REQUIRED", "门禁集合不能为空引用"));
        if (source.getRuleAssets() == null) issues.add(new Issue("ruleAssets", "REQUIRED", "规则资产集合不能为空引用"));
    }

    private void validateNodes(TemplateDesignerDocument source, List<Issue> issues) {
        Set<String> stageCodes = new HashSet<>();
        Set<String> nodeKeys = new HashSet<>();
        for (int i = 0; i < source.getStages().size(); i++) {
            TemplateDesignerDocument.StageNode stage = source.getStages().get(i);
            String path = "stages[" + i + "]";
            if (stage == null) { issues.add(new Issue(path, "REQUIRED", "阶段不能为空")); continue; }
            if (!code(stage.getNodeKey()) || !nodeKeys.add(stage.getNodeKey()))
                issues.add(new Issue(path + ".nodeKey", "INVALID_NODE_KEY", "阶段nodeKey不能为空且必须唯一"));
            if (!DeliveryDefinitionPayloadValidator.stageCode(stage.getCode()) || !stageCodes.add(stage.getCode()))
                issues.add(new Issue(path + ".code", "INVALID_STAGE_CODE", "阶段编码须唯一且不超过32个字符"));
            if (blank(stage.getName())) issues.add(new Issue(path + ".name", "REQUIRED", "阶段名称不能为空"));
            if (stage.getLifecycleStage() == null || !stage.getLifecycleStage().matches("S[0-6]"))
                issues.add(new Issue(path + ".lifecycleStage", "INVALID_LIFECYCLE_STAGE", "请选择标准生命周期阶段 S0～S6"));
        }
        Set<String> taskCodes = new HashSet<>();
        Map<String, TemplateDesignerDocument.TaskNode> tasksByCode = new HashMap<>();
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            String path = "tasks[" + i + "]";
            if (task == null) { issues.add(new Issue(path, "REQUIRED", "任务不能为空")); continue; }
            if (!code(task.getNodeKey()) || !nodeKeys.add(task.getNodeKey()))
                issues.add(new Issue(path + ".nodeKey", "INVALID_NODE_KEY", "任务nodeKey不能为空且必须唯一"));
            if (!code(task.getCode()) || !taskCodes.add(task.getCode()))
                issues.add(new Issue(path + ".code", "INVALID_TASK_CODE", "任务编码不能为空且必须唯一"));
            if (blank(task.getName())) issues.add(new Issue(path + ".name", "REQUIRED", "任务名称不能为空"));
            if (!stageCodes.contains(task.getStageCode()))
                issues.add(new Issue(path + ".stageCode", "DANGLING_STAGE", "任务引用的阶段不存在"));
            if (task.getCode() != null) tasksByCode.put(task.getCode(), task);
        }
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            if (task == null || blank(task.getParentTaskCode())) continue;
            if (!tasksByCode.containsKey(task.getParentTaskCode()))
                issues.add(new Issue("tasks[" + i + "].parentTaskCode", "DANGLING_PARENT", "父任务不存在"));
            Set<String> path = new HashSet<>();
            TemplateDesignerDocument.TaskNode current = task;
            while (current != null && current.getCode() != null) {
                if (!path.add(current.getCode())) {
                    issues.add(new Issue("tasks[" + i + "].parentTaskCode", "TASK_CYCLE", "任务父子关系构成循环"));
                    break;
                }
                current = tasksByCode.get(current.getParentTaskCode());
            }
        }
        validateSimpleNodeCodes(source.getMilestones().stream().map(TemplateDesignerDocument.MilestoneNode::getCode).toList(),
                "milestones", issues);
        validateSimpleNodeCodes(source.getDeliverables().stream().map(TemplateDesignerDocument.DeliverableNode::getCode).toList(),
                "deliverables", issues);
        validateSimpleNodeCodes(source.getGates().stream().map(TemplateDesignerDocument.GateNode::getCode).toList(),
                "gates", issues);
    }

    private void validateSimpleNodeCodes(List<String> codes, String path, List<Issue> issues) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            if (!code(code) || !seen.add(code))
                issues.add(new Issue(path + "[" + i + "].code", "INVALID_CODE", "编码不能为空且必须唯一"));
        }
    }

    private void validateGraph(TemplateDesignerDocument source, List<Issue> issues) {
        if (source.getStages().isEmpty()) issues.add(new Issue("stages", "REQUIRED", "至少配置一个交付阶段"));
        Set<String> stages = source.getStages().stream().filter(Objects::nonNull)
                .map(TemplateDesignerDocument.StageNode::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> edgeKeys = new HashSet<>();
        Set<String> edgeCodes = new HashSet<>();
        for (int i = 0; i < source.getTransitions().size(); i++) {
            TemplateDesignerDocument.TransitionNode edge = source.getTransitions().get(i);
            if (edge == null) continue;
            if (edge.getCondition() != null || !blank(edge.getConditionRuleKey()))
                issues.add(new Issue("transitions[" + i + "].condition", "EDGE_RULE_MUST_USE_ADMISSION",
                        "连线只展示准入依赖；请在目标阶段准入条件中配置判断，不再单独维护连线规则"));
            if (!code(edge.getEdgeKey()) || !edgeKeys.add(edge.getEdgeKey()))
                issues.add(new Issue("transitions[" + i + "].edgeKey", "INVALID_EDGE_KEY", "关系edgeKey不能为空且必须唯一"));
            if (!code(edge.getCode()) || !edgeCodes.add(edge.getCode()))
                issues.add(new Issue("transitions[" + i + "].code", "INVALID_EDGE_CODE", "关系编码不能为空且必须唯一"));
            if (!stages.contains(edge.getFromStageCode()) || !stages.contains(edge.getToStageCode()))
                issues.add(new Issue("transitions[" + i + "]", "DANGLING_STAGE", "依赖引用的阶段不存在"));
            if (Objects.equals(edge.getFromStageCode(), edge.getToStageCode()))
                issues.add(new Issue("transitions[" + i + "]", "SELF_DEPENDENCY", "阶段不能依赖自身完成后才准入"));
        }
    }

    private void validateRules(TemplateDesignerDocument source, List<Issue> issues) {
        Map<String, Set<String>> targets = new HashMap<>();
        targets.put("TASK", collectTaskCodes(source));
        targets.put("MILESTONE", collectMilestoneCodes(source));
        targets.put("DELIVERABLE", collectDeliverableCodes(source));
        Set<String> states = new HashSet<>();
        source.getStages().stream().filter(Objects::nonNull).map(TemplateDesignerDocument.StageNode::getCode)
                .filter(Objects::nonNull).forEach(code -> states.add(code + "_COMPLETED"));
        targets.put("STATE", states);
        var versionRules = cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.index(source.getRules());
        for (var rule : source.getRules()) {
            if (rule.kind() != cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION) continue;
            try {
                JsonNode expression = cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.condition(versionRules, rule.key());
                ruleCompiler.compile(expression);
                validateRuleTargets(expression, "rules." + rule.key(), targets, issues);
            } catch (RuntimeException invalid) {
                issues.add(new Issue("rules." + rule.key(), "INVALID_RULE", invalid.getMessage()));
            }
        }
        for (String key : new String[]{source.getMatchRuleKey(), source.getClosureRuleKey()})
            requireConditionRule(versionRules, key, "rules", issues);

        for (int i = 0; i < source.getStages().size(); i++) {
            TemplateDesignerDocument.StageNode stage = source.getStages().get(i);
            if (stage != null) {
                validateRule(stage.getCompletionRule(), "stages[" + i + "].completionRule", targets, issues);
                requireConditionRule(versionRules, stage.getAdmissionRuleKey(), "stages[" + i + "].admissionRuleKey", issues);
                requireConditionRule(versionRules, stage.getExitRuleKey(), "stages[" + i + "].exitRuleKey", issues);
            }
        }
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            if (task != null) {
                validateRule(task.getCompletionRule(), "tasks[" + i + "].completionRule", targets, issues);
                requireConditionRule(versionRules, task.getAdmissionRuleKey(), "tasks[" + i + "].admissionRuleKey", issues);
                requireConditionRule(versionRules, task.getExitRuleKey(), "tasks[" + i + "].exitRuleKey", issues);
            }
        }
        Set<String> assetKeys = new HashSet<>();
        for (int i = 0; i < source.getRuleAssets().size(); i++) {
            TemplateDesignerDocument.RuleAsset asset = source.getRuleAssets().get(i);
            if (asset == null) { issues.add(new Issue("ruleAssets[" + i + "]", "REQUIRED", "规则资产不能为空")); continue; }
            if (!code(asset.getKey()) || !assetKeys.add(asset.getKey()))
                issues.add(new Issue("ruleAssets[" + i + "].key", "INVALID_RULE_KEY", "规则资产key不能为空且必须唯一"));
            validateRule(asset.getRule(), "ruleAssets[" + i + "].rule", targets, issues);
        }
    }

    private void requireConditionRule(Map<String, cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule> rules,
                                      String key, String path, List<Issue> issues) {
        if (key == null || key.isBlank()) return;
        if (!rules.containsKey(key) || rules.get(key).kind() != cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION)
            issues.add(new Issue(path, "CONDITION_RESULT_REQUIRED", "这里需要满足/不满足/未知的条件；策略输出需先配置比较条件"));
    }

    private void validateRule(TemplateDesignerDocument.RuleSpec rule, String path,
                              Map<String, Set<String>> targets, List<Issue> issues) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().isNull()) return;
        try {
            ruleCompiler.compile(rule.getExpression());
            validateRuleTargets(rule.getExpression(), path, targets, issues);
        } catch (IllegalArgumentException ex) {
            issues.add(new Issue(path, "INVALID_RULE", ex.getMessage()));
        }
    }

    private void validateRuleTargets(JsonNode rule, String path, Map<String, Set<String>> targets, List<Issue> issues) {
        if (rule.has("operator")) {
            int index = 0;
            for (JsonNode child : rule.path("rules"))
                validateRuleTargets(child, path + ".rules[" + index++ + "]", targets, issues);
            return;
        }
        String predicate = rule.path("predicate").asText();
        Set<String> allowed = targets.get(predicate);
        if (allowed == null) return;
        String ref = rule.path("parameters").path("refCode").asText();
        if (!allowed.contains(ref))
            issues.add(new Issue(path + ".parameters.refCode", "RULE_TARGET_NOT_CONFIGURED",
                    predicate + "引用目标未配置在当前模板：" + ref));
    }

    private void validateBindings(TemplateDesignerDocument source, List<Issue> issues) {
        for (int i = 0; i < source.getStages().size(); i++) {
            TemplateDesignerDocument.StageNode stage = source.getStages().get(i);
            if (stage == null) continue;
            if (stage.getWorkBinding() != null) {
                validateBinding(stage.getWorkBinding(), true, "stages[" + i + "].workBinding", issues);
                validatePermission(stage.getPermission(), "stages[" + i + "].permission", issues);
            }
            if (stage.getCompletionRule() == null || stage.getCompletionRule().getExpression() == null)
                issues.add(new Issue("stages[" + i + "].completionRule", "REQUIRED", "阶段必须配置完成规则"));
            else if (stage.getWorkBinding() == null && isNativeCompletion(stage.getCompletionRule().getExpression()))
                issues.add(new Issue("stages[" + i + "].completionRule", "STAGE_HANDLING_NOT_CONFIGURED",
                        "仅组织任务的阶段应按任务或业务结果完成，不能依赖阶段自身手工办理"));
        }
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            if (task == null) continue;
            validateBinding(task.getWorkBinding(), false, "tasks[" + i + "].workBinding", issues);
            validatePermission(task.getPermission(), "tasks[" + i + "].permission", issues);
            if (task.getCompletionRule() == null || task.getCompletionRule().getExpression() == null) {
                issues.add(new Issue("tasks[" + i + "].completionRule", "REQUIRED", "任务必须配置完成规则"));
            } else if (task.getWorkBinding() != null && !isNative(task.getWorkBinding().getType())
                    && isNativeCompletion(task.getCompletionRule().getExpression())) {
                issues.add(new Issue("tasks[" + i + "].completionRule", "OWNER_FACT_REQUIRED",
                        "非原生绑定必须使用真实Owner完成事实，不能沿用原生手工完成"));
            }
        }
    }

    private void validateBinding(TemplateDesignerDocument.WorkBindingSpec binding, boolean stage,
                                 String path, List<Issue> issues) {
        if (binding == null) { issues.add(new Issue(path, "REQUIRED", "必须配置唯一主WorkBinding")); return; }
        if (!DeliveryDefinitionPayloadValidator.BINDING_TYPES.contains(binding.getType())) {
            issues.add(new Issue(path + ".type", "INVALID_BINDING", "WorkBinding类型无效"));
            return;
        }
        if (stage && "TASK_NATIVE".equals(binding.getType()))
            issues.add(new Issue(path + ".type", "BINDING_OWNER_MISMATCH", "阶段不能使用TASK_NATIVE"));
        if (!stage && "STAGE_NATIVE".equals(binding.getType()))
            issues.add(new Issue(path + ".type", "BINDING_OWNER_MISMATCH", "任务不能使用STAGE_NATIVE"));
        if (isNative(binding.getType())) {
            if (!blank(binding.getTargetContextCode()) || !blank(binding.getTargetObjectType())
                    || !blank(binding.getTargetObjectKey()) || !blank(binding.getComponentKey())
                    || binding.getDynamicFormRevisionId() != null || !blank(binding.getApprovalDefinitionKey()))
                issues.add(new Issue(path, "NATIVE_TARGET_FORBIDDEN", "原生WorkBinding不得配置外部目标"));
            return;
        }
        switch (binding.getType()) {
            case "BUSINESS_OBJECT" -> {
                required(binding.getTargetContextCode(), path + ".targetContextCode", "BUSINESS_OBJECT缺少Owner", issues);
                required(binding.getTargetObjectType(), path + ".targetObjectType", "BUSINESS_OBJECT缺少对象类型", issues);
                required(binding.getTargetObjectKey(), path + ".targetObjectKey", "BUSINESS_OBJECT缺少对象解析键", issues);
            }
            case "BUSINESS_COMPONENT" -> required(binding.getComponentKey(), path + ".componentKey",
                    "BUSINESS_COMPONENT缺少组件键", issues);
            case "DYNAMIC_FORM" -> {
                if (binding.getDynamicFormRevisionId() == null || binding.getDynamicFormRevisionId() <= 0)
                    issues.add(new Issue(path + ".dynamicFormRevisionId", "REQUIRED", "DYNAMIC_FORM缺少精确发布修订"));
            }
            case "APPROVAL" -> {
                try {
                    ApprovalWorkBindingSchema.read(
                            binding.getApprovalDefinitionKey(), binding.getParameters());
                } catch (IllegalArgumentException invalid) {
                    issues.add(new Issue(path, "APPROVAL_DEFINITION_REQUIRED", invalid.getMessage()));
                }
            }
            case "COMPOSITE" -> {
                if (binding.getParameters() == null || !binding.getParameters().isObject())
                    issues.add(new Issue(path + ".parameters", "REQUIRED", "COMPOSITE必须配置受控子视图参数"));
            }
            default -> { }
        }
    }

    private void validatePermission(TemplateDesignerDocument.PermissionRequirement permission,
                                    String path, List<Issue> issues) {
        if (permission == null || (blank(permission.getPolicyRef()) && permission.getPolicySnapshot() == null)) {
            issues.add(new Issue(path, "REQUIRED", "必须声明权限需求，最终授权仍由Owner实时计算"));
            return;
        }
        if (permission.getPolicySnapshot() != null) {
            try {
                // Inline declarations and copied assets must obey the same payload contract.
                DeliveryDefinitionPayloadValidator.validate(DeliveryDefinitionKind.PERMISSION_POLICY, 1,
                        permission.getPolicySnapshot(), List.of());
            } catch (IllegalArgumentException invalid) {
                issues.add(new Issue(path + ".policySnapshot", "INVALID_PERMISSION_POLICY",
                        "权限声明格式无效：" + invalid.getMessage()));
            }
        }
    }

    private void validateGateReferences(TemplateDesignerDocument source, List<Issue> issues) {
        Set<String> stages = source.getStages().stream().filter(Objects::nonNull).map(TemplateDesignerDocument.StageNode::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> tasks = collectTaskCodes(source);
        Set<String> milestones = collectMilestoneCodes(source);
        Set<String> deliverables = collectDeliverableCodes(source);
        Set<String> gates = new HashSet<>();
        for (int i = 0; i < source.getGates().size(); i++) {
            TemplateDesignerDocument.GateNode gate = source.getGates().get(i);
            String path = "gates[" + i + "]";
            if (gate == null) continue;
            if (!gates.add(gate.getCode())) continue;
            if (!Set.of("ENTRY", "EXIT").contains(gate.getGateType()))
                issues.add(new Issue(path + ".gateType", "INVALID_GATE_TYPE", "Gate类型必须为ENTRY或EXIT"));
            if (!stages.contains(gate.getStageCode()))
                issues.add(new Issue(path + ".stageCode", "DANGLING_STAGE", "Gate所属阶段不存在"));
            if (gate.getReferences() == null || gate.getReferences().isEmpty()) {
                issues.add(new Issue(path + ".references", "REQUIRED", "Gate至少需要一个引用"));
                continue;
            }
            Set<String> referenceKeys = new HashSet<>();
            for (int j = 0; j < gate.getReferences().size(); j++) {
                TemplateDesignerDocument.GateReference ref = gate.getReferences().get(j);
                String refPath = path + ".references[" + j + "]";
                if (ref == null) { issues.add(new Issue(refPath, "REQUIRED", "Gate引用不能为空")); continue; }
                if (!referenceKeys.add(ref.getRefType() + ":" + ref.getRefCode()))
                    issues.add(new Issue(refPath, "DUPLICATE_GATE_REFERENCE", "同一门禁不能重复引用同一对象"));
                boolean valid = switch (ref.getRefType() == null ? "" : ref.getRefType()) {
                    case "TASK" -> tasks.contains(ref.getRefCode());
                    case "MILESTONE" -> milestones.contains(ref.getRefCode());
                    case "DELIVERABLE" -> deliverables.contains(ref.getRefCode());
                    case "STATE" -> ref.getRefCode() != null && ref.getRefCode().endsWith("_COMPLETED")
                            && stages.contains(ref.getRefCode().substring(0, ref.getRefCode().length() - "_COMPLETED".length()));
                    case "APPROVAL", "PROCESS" -> code(ref.getRefCode());
                    default -> false;
                };
                if (!valid) issues.add(new Issue(refPath, "INVALID_GATE_REFERENCE", "Gate引用不存在或类型无效"));
                if (("APPROVAL".equals(ref.getRefType()) || "PROCESS".equals(ref.getRefType()))
                        && (ref.getRefVersion() == null || ref.getRefVersion().isBlank()))
                    issues.add(new Issue(refPath + ".refVersion", "GATE_PROCESS_DEFINITION_REQUIRED", "流程引用须冻结精确流程定义ID，不能运行时选择最新版本"));
            }
        }
        for (int i = 0; i < source.getTasks().size(); i++) {
            var task = source.getTasks().get(i);
            if (task != null && task.getGateRef() != null && !task.getGateRef().isBlank() && !gates.contains(task.getGateRef()))
                issues.add(new Issue("tasks[" + i + "].gateRef", "DANGLING_GATE", "任务引用的门禁不存在"));
        }
    }

    private TemplateExecutionSnapshot buildSnapshot(TemplateDesignerDocument source) {
        TemplateExecutionSnapshot result = new TemplateExecutionSnapshot();
        result.setCompilerVersion(COMPILER_VERSION);
        result.setRules(List.copyOf(source.getRules()));
        result.setMatchRuleKey(source.getMatchRuleKey());
        result.setClosureRuleKey(source.getClosureRuleKey());
        var versionRules = cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.index(source.getRules());
        for (var entry : versionRules.entrySet()) {
            var rule = entry.getValue();
            var program = rule.kind() == cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION
                    ? ruleCompiler.compile(cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.condition(versionRules, rule.key()))
                    : ruleCompiler.compileDecision(rule);
            result.getRulePrograms().put(entry.getKey(), program);
        }
        result.setMatch(copyMatch(source.getMatch()));
        result.setProcessDefinitionKey(source.getProcessDefinitionKey());
        result.setClosurePolicy(copy(source.getClosurePolicy()));
        for (TemplateDesignerDocument.StageNode sourceStage : source.getStages()) result.getStages().add(stage(sourceStage));
        for (TemplateDesignerDocument.TaskNode sourceTask : source.getTasks()) result.getTasks().add(task(sourceTask));
        for (TemplateDesignerDocument.MilestoneNode sourceNode : source.getMilestones()) result.getMilestones().add(milestone(sourceNode));
        for (TemplateDesignerDocument.DeliverableNode sourceNode : source.getDeliverables()) result.getDeliverables().add(deliverable(sourceNode));
        for (TemplateDesignerDocument.GateNode sourceGate : source.getGates()) {
            var gate = gate(sourceGate);
            // Reserved execution-artifact key cannot collide with user-authored version-local rule keys.
            gate.setConditionRuleKey("$gate:" + gate.getNodeKey());
            result.getRulePrograms().put(gate.getConditionRuleKey(), ruleCompiler.compileGateReferences(sourceGate.getReferences()));
            result.getGates().add(gate);
        }
        for (TemplateDesignerDocument.TransitionNode sourceEdge : source.getTransitions()) result.getTransitions().add(transition(sourceEdge));
        return result;
    }

    private TemplateExecutionSnapshot.StageContract stage(TemplateDesignerDocument.StageNode source) {
        TemplateExecutionSnapshot.StageContract target = new TemplateExecutionSnapshot.StageContract();
        target.setLifecycleStage(source.getLifecycleStage());
        target.setAdmissionRuleKey(source.getAdmissionRuleKey()); target.setCompletionRuleKey(source.getCompletionRuleKey()); target.setExitRuleKey(source.getExitRuleKey());
        target.setNodeKey(source.getNodeKey()); target.setCode(source.getCode()); target.setName(source.getName());
        target.setSortOrder(source.getSortOrder()); target.setEntryCriteria(source.getEntryCriteria()); target.setExitCriteria(source.getExitCriteria());
        target.setStart(source.getStart()); target.setTerminal(source.getTerminal()); target.setBinding(binding(source.getWorkBinding()));
        target.setPermission(permission(source.getPermission())); target.setCompletionRule(rule(source.getCompletionRule()));
        if (source.getSource() != null) {
            target.setSourceDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
            target.setSourceWorkBindingRevisionId(source.getSource().getWorkBindingRevisionId());
            target.setSourcePermissionPolicyRevisionId(source.getSource().getPermissionPolicyRevisionId());
            target.setSourceCompletionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
        }
        return target;
    }

    private TemplateExecutionSnapshot.TaskContract task(TemplateDesignerDocument.TaskNode source) {
        TemplateExecutionSnapshot.TaskContract target = new TemplateExecutionSnapshot.TaskContract();
        target.setAdmissionRuleKey(source.getAdmissionRuleKey()); target.setCompletionRuleKey(source.getCompletionRuleKey()); target.setExitRuleKey(source.getExitRuleKey());
        target.setNodeKey(source.getNodeKey()); target.setCode(source.getCode()); target.setName(source.getName());
        target.setParentTaskCode(source.getParentTaskCode()); target.setStageCode(source.getStageCode()); target.setPriority(source.getPriority());
        target.setSortOrder(source.getSortOrder()); target.setEstimatedHours(source.getEstimatedHours()); target.setSatisfactionTiming(source.getSatisfactionTiming());
        target.setDescription(source.getDescription()); target.setBinding(binding(source.getWorkBinding())); target.setPermission(permission(source.getPermission()));
        target.setCompletionRule(rule(source.getCompletionRule())); target.setGateRef(source.getGateRef());
        if (source.getSource() != null) {
            target.setSourceDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
            target.setSourceWorkBindingRevisionId(source.getSource().getWorkBindingRevisionId());
            target.setSourcePermissionPolicyRevisionId(source.getSource().getPermissionPolicyRevisionId());
            target.setSourceCompletionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
        }
        return target;
    }

    private TemplateExecutionSnapshot.BindingContract binding(TemplateDesignerDocument.WorkBindingSpec source) {
        if (source == null) return null;
        TemplateExecutionSnapshot.BindingContract target = new TemplateExecutionSnapshot.BindingContract();
        target.setType(source.getType()); target.setTargetContextCode(source.getTargetContextCode()); target.setTargetObjectType(source.getTargetObjectType());
        target.setTargetObjectKey(source.getTargetObjectKey()); target.setComponentKey(source.getComponentKey()); target.setDynamicFormRevisionId(source.getDynamicFormRevisionId());
        target.setApprovalDefinitionKey(source.getApprovalDefinitionKey()); target.setParameters(copy(source.getParameters()));
        target.setBusinessViewSnapshot(copy(source.getBusinessViewSnapshot())); target.setSourceRevisionId(source.getSourceRevisionId());
        return target;
    }

    private TemplateExecutionSnapshot.PermissionContract permission(TemplateDesignerDocument.PermissionRequirement source) {
        if (source == null) return null;
        TemplateExecutionSnapshot.PermissionContract target = new TemplateExecutionSnapshot.PermissionContract();
        target.setPolicyRef(source.getPolicyRef()); target.setPolicySnapshot(copy(source.getPolicySnapshot())); target.setSourceRevisionId(source.getSourceRevisionId());
        return target;
    }

    private JsonNode rule(TemplateDesignerDocument.RuleSpec source) { return source == null ? null : copy(source.getExpression()); }

    private TemplateExecutionSnapshot.TransitionContract transition(TemplateDesignerDocument.TransitionNode source) {
        TemplateExecutionSnapshot.TransitionContract target = new TemplateExecutionSnapshot.TransitionContract();
        target.setConditionRuleKey(source.getConditionRuleKey());
        target.setEdgeKey(source.getEdgeKey()); target.setCode(source.getCode()); target.setFromStageCode(source.getFromStageCode());
        target.setToStageCode(source.getToStageCode()); target.setConditionRule(rule(source.getCondition())); target.setPriority(source.getPriority());
        target.setDefaultBranch(source.getDefaultBranch());
        if (source.getSource() != null) {
            target.setSourceTransitionId(source.getSource().getTransitionId());
            target.setSourceTransitionRevisionNo(source.getSource().getTransitionRevisionNo());
            target.setSourceConditionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
        }
        return target;
    }

    private TemplateExecutionSnapshot.MilestoneContract milestone(TemplateDesignerDocument.MilestoneNode source) {
        TemplateExecutionSnapshot.MilestoneContract target = new TemplateExecutionSnapshot.MilestoneContract();
        target.setNodeKey(source.getNodeKey()); target.setCode(source.getCode()); target.setName(source.getName()); target.setStageCode(source.getStageCode());
        target.setTiming(source.getTiming()); target.setCriteria(source.getCriteria()); target.setConfiguration(copy(source.getConfiguration()));
        if (source.getSource() != null) target.setSourceDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
        return target;
    }

    private TemplateExecutionSnapshot.DeliverableContract deliverable(TemplateDesignerDocument.DeliverableNode source) {
        TemplateExecutionSnapshot.DeliverableContract target = new TemplateExecutionSnapshot.DeliverableContract();
        target.setNodeKey(source.getNodeKey()); target.setCode(source.getCode()); target.setName(source.getName()); target.setStageCode(source.getStageCode());
        target.setTaskCode(source.getTaskCode()); target.setRequired(source.getRequired()); target.setConfiguration(copy(source.getConfiguration()));
        if (source.getSource() != null) target.setSourceDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
        return target;
    }

    private TemplateExecutionSnapshot.GateContract gate(TemplateDesignerDocument.GateNode source) {
        TemplateExecutionSnapshot.GateContract target = new TemplateExecutionSnapshot.GateContract();
        target.setNodeKey(source.getNodeKey()); target.setCode(source.getCode()); target.setName(source.getName()); target.setGateType(source.getGateType());
        target.setStageCode(source.getStageCode()); target.setDescription(source.getDescription());
        for (TemplateDesignerDocument.GateReference sourceRef : source.getReferences()) {
            TemplateExecutionSnapshot.GateReference ref = new TemplateExecutionSnapshot.GateReference();
            ref.setRefType(sourceRef.getRefType()); ref.setRefCode(sourceRef.getRefCode()); ref.setRefVersion(sourceRef.getRefVersion()); target.getReferences().add(ref);
        }
        if (source.getSource() != null) target.setSourceDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
        return target;
    }

    private TemplateDesignerDocument.Match copyMatch(TemplateDesignerDocument.Match source) {
        TemplateDesignerDocument.Match target = new TemplateDesignerDocument.Match();
        if (source != null) {
            target.setSigningMethod(source.getSigningMethod()); target.setProjectCategory(source.getProjectCategory());
            target.setImplementationMethod(source.getImplementationMethod()); target.setMajorProjectLevel(source.getMajorProjectLevel());
        }
        return target;
    }

    private JsonNode copy(JsonNode source) { return source == null || source.isNull() ? null : source.deepCopy(); }

    private Set<String> collectTaskCodes(TemplateDesignerDocument source) {
        Set<String> result = new HashSet<>(); source.getTasks().stream().filter(Objects::nonNull).map(TemplateDesignerDocument.TaskNode::getCode).filter(Objects::nonNull).forEach(result::add); return result;
    }
    private Set<String> collectMilestoneCodes(TemplateDesignerDocument source) {
        Set<String> result = new HashSet<>(); source.getMilestones().stream().filter(Objects::nonNull).map(TemplateDesignerDocument.MilestoneNode::getCode).filter(Objects::nonNull).forEach(result::add); return result;
    }
    private Set<String> collectDeliverableCodes(TemplateDesignerDocument source) {
        Set<String> result = new HashSet<>(); source.getDeliverables().stream().filter(Objects::nonNull).map(TemplateDesignerDocument.DeliverableNode::getCode).filter(Objects::nonNull).forEach(result::add); return result;
    }
    private boolean isNative(String type) { return "STAGE_NATIVE".equals(type) || "TASK_NATIVE".equals(type); }
    private boolean isNativeCompletion(JsonNode rule) {
        if (rule == null) return false;
        try {
            return ruleCompiler.compile(rule).leaves().stream().anyMatch(leaf -> leaf.predicate().endsWith("_NATIVE_STATUS"));
        } catch (RuntimeException invalid) {
            return false; // Invalid grammar is already reported by validateRules, never treated as a valid publication.
        }
    }
    private boolean code(String value) { return DeliveryDefinitionPayloadValidator.code(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void required(String value, String path, String message, List<Issue> issues) { if (blank(value)) issues.add(new Issue(path, "REQUIRED", message)); }
}
