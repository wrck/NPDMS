package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionDefinition;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionGraph;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionGraphValidator;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionPayloadValidator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** PM-03 V2 deterministic authoring -> execution compiler. */
@Component
public class TemplateCompiler {

    public static final String COMPILER_VERSION = "template-compiler-v2.0";

    public record Compilation(TemplateExecutionSnapshot snapshot, String snapshotHash, List<Issue> issues) {
        public boolean valid() { return issues.isEmpty(); }
    }

    public Compilation compile(TemplateDesignerDocument source) {
        List<Issue> issues = new ArrayList<>();
        if (source == null) {
            issues.add(new Issue("designer", "REQUIRED", "模板设计文档不能为空"));
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
        if (!issues.isEmpty()) return new Compilation(null, null, List.copyOf(issues));

        TemplateExecutionSnapshot snapshot = buildSnapshot(source);
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(semanticSnapshot(snapshot)));
        return new Compilation(snapshot, hash, List.of());
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
        Set<String> stageKeys = new HashSet<>();
        for (int i = 0; i < source.getStages().size(); i++) {
            TemplateDesignerDocument.StageNode stage = source.getStages().get(i);
            String path = "stages[" + i + "]";
            if (stage == null) { issues.add(new Issue(path, "REQUIRED", "阶段不能为空")); continue; }
            if (!code(stage.getNodeKey()) || !stageKeys.add(stage.getNodeKey()))
                issues.add(new Issue(path + ".nodeKey", "INVALID_NODE_KEY", "阶段nodeKey不能为空且必须唯一"));
            if (stage.getCode() == null || !stage.getCode().matches("S[0-6]") || !stageCodes.add(stage.getCode()))
                issues.add(new Issue(path + ".code", "INVALID_STAGE_CODE", "阶段编码必须为唯一S0～S6"));
            if (blank(stage.getName())) issues.add(new Issue(path + ".name", "REQUIRED", "阶段名称不能为空"));
            if (stage.getStart() == null) issues.add(new Issue(path + ".start", "REQUIRED", "必须显式声明开始阶段"));
            if (stage.getTerminal() == null) issues.add(new Issue(path + ".terminal", "REQUIRED", "必须显式声明正常收口阶段"));
        }
        Set<String> taskCodes = new HashSet<>();
        Set<String> taskKeys = new HashSet<>();
        Map<String, TemplateDesignerDocument.TaskNode> tasksByCode = new HashMap<>();
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            String path = "tasks[" + i + "]";
            if (task == null) { issues.add(new Issue(path, "REQUIRED", "任务不能为空")); continue; }
            if (!code(task.getNodeKey()) || !taskKeys.add(task.getNodeKey()))
                issues.add(new Issue(path + ".nodeKey", "INVALID_NODE_KEY", "任务nodeKey不能为空且必须唯一"));
            if (!code(task.getCode()) || !taskCodes.add(task.getCode()))
                issues.add(new Issue(path + ".code", "INVALID_TASK_CODE", "任务编码不能为空且必须唯一"));
            if (blank(task.getName())) issues.add(new Issue(path + ".name", "REQUIRED", "任务名称不能为空"));
            if (!stageCodes.contains(task.getStageCode()))
                issues.add(new Issue(path + ".stageCode", "DANGLING_STAGE", "任务引用的阶段不存在"));
            if ("S0".equals(task.getStageCode()))
                issues.add(new Issue(path + ".stageCode", "S0_TASK_FORBIDDEN", "S0项目基本操作不得重复配置为ProjectTask"));
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
        StageTransitionGraph graph = new StageTransitionGraph(
                source.getStages().stream().map(stage -> stage == null ? null
                        : new StageTransitionGraph.Stage(stage.getCode(), stage.getStart(), stage.getTerminal())).toList(),
                source.getTransitions().stream().map(edge -> edge == null ? null
                        : new StageTransitionDefinition(edge.getCode(), edge.getFromStageCode(), edge.getToStageCode(),
                        edge.getCondition() == null || edge.getCondition().getExpression() == null ? null : 1L,
                        edge.getPriority(), edge.getDefaultBranch())).toList());
        for (StageTransitionGraphValidator.Failure failure : StageTransitionGraphValidator.validate(graph)) {
            issues.add(new Issue(failure.path(), failure.code(), failure.message()));
        }
        Set<String> edgeKeys = new HashSet<>();
        for (int i = 0; i < source.getTransitions().size(); i++) {
            TemplateDesignerDocument.TransitionNode edge = source.getTransitions().get(i);
            if (edge == null) continue;
            if (!code(edge.getEdgeKey()) || !edgeKeys.add(edge.getEdgeKey()))
                issues.add(new Issue("transitions[" + i + "].edgeKey", "INVALID_EDGE_KEY", "关系edgeKey不能为空且必须唯一"));
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

        for (int i = 0; i < source.getStages().size(); i++) {
            TemplateDesignerDocument.StageNode stage = source.getStages().get(i);
            if (stage != null) validateRule(stage.getCompletionRule(), "stages[" + i + "].completionRule", targets, issues);
        }
        for (int i = 0; i < source.getTasks().size(); i++) {
            TemplateDesignerDocument.TaskNode task = source.getTasks().get(i);
            if (task != null) validateRule(task.getCompletionRule(), "tasks[" + i + "].completionRule", targets, issues);
        }
        for (int i = 0; i < source.getTransitions().size(); i++) {
            TemplateDesignerDocument.TransitionNode edge = source.getTransitions().get(i);
            if (edge != null) validateRule(edge.getCondition(), "transitions[" + i + "].condition", targets, issues);
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

    private void validateRule(TemplateDesignerDocument.RuleSpec rule, String path,
                              Map<String, Set<String>> targets, List<Issue> issues) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().isNull()) return;
        try {
            DeliveryDefinitionPayloadValidator.rule(rule.getExpression());
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
            validateBinding(stage.getWorkBinding(), true, "stages[" + i + "].workBinding", issues);
            validatePermission(stage.getPermission(), "stages[" + i + "].permission", issues);
            if (stage.getCompletionRule() == null || stage.getCompletionRule().getExpression() == null)
                issues.add(new Issue("stages[" + i + "].completionRule", "REQUIRED", "阶段必须配置完成规则"));
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
            case "APPROVAL" -> required(binding.getApprovalDefinitionKey(), path + ".approvalDefinitionKey",
                    "APPROVAL缺少流程定义key", issues);
            case "COMPOSITE" -> {
                if (binding.getParameters() == null || !binding.getParameters().isObject())
                    issues.add(new Issue(path + ".parameters", "REQUIRED", "COMPOSITE必须配置受控子视图参数"));
            }
            default -> { }
        }
    }

    private void validatePermission(TemplateDesignerDocument.PermissionRequirement permission,
                                    String path, List<Issue> issues) {
        if (permission == null || (blank(permission.getPolicyRef()) && permission.getPolicySnapshot() == null))
            issues.add(new Issue(path, "REQUIRED", "必须声明权限需求，最终授权仍由Owner实时计算"));
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
            for (int j = 0; j < gate.getReferences().size(); j++) {
                TemplateDesignerDocument.GateReference ref = gate.getReferences().get(j);
                String refPath = path + ".references[" + j + "]";
                if (ref == null) { issues.add(new Issue(refPath, "REQUIRED", "Gate引用不能为空")); continue; }
                boolean valid = switch (ref.getRefType() == null ? "" : ref.getRefType()) {
                    case "TASK" -> tasks.contains(ref.getRefCode());
                    case "MILESTONE" -> milestones.contains(ref.getRefCode());
                    case "DELIVERABLE" -> deliverables.contains(ref.getRefCode());
                    case "STATE" -> ref.getRefCode() != null && ref.getRefCode().matches("S[0-6]_COMPLETED")
                            && stages.contains(ref.getRefCode().substring(0, 2));
                    case "APPROVAL", "PROCESS" -> code(ref.getRefCode());
                    default -> false;
                };
                if (!valid) issues.add(new Issue(refPath, "INVALID_GATE_REFERENCE", "Gate引用不存在或类型无效"));
            }
        }
    }

    private TemplateExecutionSnapshot buildSnapshot(TemplateDesignerDocument source) {
        TemplateExecutionSnapshot result = new TemplateExecutionSnapshot();
        result.setCompilerVersion(COMPILER_VERSION);
        result.setMatch(copyMatch(source.getMatch()));
        result.setProcessDefinitionKey(source.getProcessDefinitionKey());
        result.setClosurePolicy(copy(source.getClosurePolicy()));
        for (TemplateDesignerDocument.StageNode sourceStage : source.getStages()) result.getStages().add(stage(sourceStage));
        for (TemplateDesignerDocument.TaskNode sourceTask : source.getTasks()) result.getTasks().add(task(sourceTask));
        for (TemplateDesignerDocument.MilestoneNode sourceNode : source.getMilestones()) result.getMilestones().add(milestone(sourceNode));
        for (TemplateDesignerDocument.DeliverableNode sourceNode : source.getDeliverables()) result.getDeliverables().add(deliverable(sourceNode));
        for (TemplateDesignerDocument.GateNode sourceGate : source.getGates()) result.getGates().add(gate(sourceGate));
        for (TemplateDesignerDocument.TransitionNode sourceEdge : source.getTransitions()) result.getTransitions().add(transition(sourceEdge));
        return result;
    }

    private TemplateExecutionSnapshot.StageContract stage(TemplateDesignerDocument.StageNode source) {
        TemplateExecutionSnapshot.StageContract target = new TemplateExecutionSnapshot.StageContract();
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

    /** Canonical business snapshot excludes legacy source ids and publication metadata. */
    private Map<String, Object> semanticSnapshot(TemplateExecutionSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("executionSchemaVersion", snapshot.getExecutionSchemaVersion());
        root.put("compilerVersion", snapshot.getCompilerVersion());
        root.put("match", snapshot.getMatch());
        root.put("processDefinitionKey", snapshot.getProcessDefinitionKey());
        root.put("closurePolicy", snapshot.getClosurePolicy());
        root.put("stages", snapshot.getStages().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.StageContract::getNodeKey)).map(this::semanticStage).toList());
        root.put("tasks", snapshot.getTasks().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.TaskContract::getNodeKey)).map(this::semanticTask).toList());
        root.put("milestones", snapshot.getMilestones().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.MilestoneContract::getNodeKey)).map(this::semanticMilestone).toList());
        root.put("deliverables", snapshot.getDeliverables().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.DeliverableContract::getNodeKey)).map(this::semanticDeliverable).toList());
        root.put("gates", snapshot.getGates().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.GateContract::getNodeKey)).map(this::semanticGate).toList());
        root.put("transitions", snapshot.getTransitions().stream().sorted(Comparator.comparing(TemplateExecutionSnapshot.TransitionContract::getEdgeKey)).map(this::semanticTransition).toList());
        return root;
    }

    private Map<String, Object> semanticStage(TemplateExecutionSnapshot.StageContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("sortOrder", row.getSortOrder()); map.put("entryCriteria", row.getEntryCriteria()); map.put("exitCriteria", row.getExitCriteria());
        map.put("start", row.getStart()); map.put("terminal", row.getTerminal()); map.put("binding", semanticBinding(row.getBinding()));
        map.put("permission", semanticPermission(row.getPermission())); map.put("completionRule", row.getCompletionRule()); return map;
    }

    private Map<String, Object> semanticTask(TemplateExecutionSnapshot.TaskContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("parentTaskCode", row.getParentTaskCode()); map.put("stageCode", row.getStageCode()); map.put("priority", row.getPriority());
        map.put("sortOrder", row.getSortOrder()); map.put("estimatedHours", row.getEstimatedHours()); map.put("satisfactionTiming", row.getSatisfactionTiming());
        map.put("description", row.getDescription()); map.put("binding", semanticBinding(row.getBinding())); map.put("permission", semanticPermission(row.getPermission()));
        map.put("completionRule", row.getCompletionRule()); map.put("gateRef", row.getGateRef()); return map;
    }

    private Map<String, Object> semanticBinding(TemplateExecutionSnapshot.BindingContract row) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>(); map.put("type", row.getType()); map.put("targetContextCode", row.getTargetContextCode());
        map.put("targetObjectType", row.getTargetObjectType()); map.put("targetObjectKey", row.getTargetObjectKey()); map.put("componentKey", row.getComponentKey());
        map.put("dynamicFormRevisionId", row.getDynamicFormRevisionId()); map.put("approvalDefinitionKey", row.getApprovalDefinitionKey());
        map.put("parameters", row.getParameters()); map.put("businessViewSnapshot", row.getBusinessViewSnapshot()); return map;
    }

    private Map<String, Object> semanticPermission(TemplateExecutionSnapshot.PermissionContract row) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>(); map.put("policyRef", row.getPolicyRef()); map.put("policySnapshot", row.getPolicySnapshot()); return map;
    }

    private Map<String, Object> semanticTransition(TemplateExecutionSnapshot.TransitionContract row) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put("edgeKey", row.getEdgeKey()); map.put("code", row.getCode());
        map.put("fromStageCode", row.getFromStageCode()); map.put("toStageCode", row.getToStageCode()); map.put("conditionRule", row.getConditionRule());
        map.put("priority", row.getPriority()); map.put("defaultBranch", row.getDefaultBranch()); return map;
    }

    private Map<String, Object> semanticMilestone(TemplateExecutionSnapshot.MilestoneContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName()); map.put("stageCode", row.getStageCode());
        map.put("timing", row.getTiming()); map.put("criteria", row.getCriteria()); map.put("configuration", row.getConfiguration()); return map;
    }

    private Map<String, Object> semanticDeliverable(TemplateExecutionSnapshot.DeliverableContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName()); map.put("stageCode", row.getStageCode());
        map.put("taskCode", row.getTaskCode()); map.put("required", row.getRequired()); map.put("configuration", row.getConfiguration()); return map;
    }

    private Map<String, Object> semanticGate(TemplateExecutionSnapshot.GateContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName()); map.put("gateType", row.getGateType());
        map.put("stageCode", row.getStageCode()); map.put("description", row.getDescription()); map.put("references", row.getReferences()); return map;
    }

    private Map<String, Object> baseNode(String key, String code, String name) {
        Map<String, Object> map = new LinkedHashMap<>(); map.put("nodeKey", key); map.put("code", code); map.put("name", name); return map;
    }

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
    private boolean isNativeCompletion(JsonNode rule) { return rule != null && rule.path("predicate").asText().endsWith("_NATIVE_STATUS"); }
    private boolean code(String value) { return DeliveryDefinitionPayloadValidator.code(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void required(String value, String path, String message, List<Issue> issues) { if (blank(value)) issues.add(new Issue(path, "REQUIRED", message)); }
}
