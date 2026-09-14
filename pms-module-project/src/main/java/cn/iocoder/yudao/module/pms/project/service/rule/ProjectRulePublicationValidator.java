package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates execution dependencies without executing business facts or publishing a second rule source. */
@Component
@RequiredArgsConstructor
public class ProjectRulePublicationValidator {
    private final ProjectRuleCompiler compiler;
    private final ProjectDecisionTableService decisions;
    private final TaskBusinessProviderRegistry businessProviders;

    /** Standalone copy sources use the same syntax, field and engine checks as version-owned conditions. */
    public List<Issue> validateCondition(tools.jackson.databind.JsonNode expression) {
        List<Issue> issues = new ArrayList<>();
        try {
            var program = compiler.compile(expression);
            validateProgram("rule", program, ProjectRuleFields.codes(), false, issues);
            for (var leaf : program.leaves()) if (leaf.parameters().has("sourceNodeKey"))
                issues.add(new Issue("rule." + leaf.path(), "RULE_SOURCE_REQUIRES_VERSION", "来源节点属于模板或项目计划版本，请在节点侧栏配置"));
        } catch (RuntimeException invalid) {
            issues.add(new Issue("rule", "RULE_EXECUTION_DEFINITION_INVALID", "规则或决策表执行定义无效，请检查表达式及字段绑定"));
        }
        return List.copyOf(issues);
    }

    public List<Issue> validate(TemplateDesignerDocument submitted) {
        List<Issue> issues = new ArrayList<>();
        TemplateDesignerDocument document;
        try {
            document = TemplateRuleCollection.forEditing(submitted);
        } catch (IllegalArgumentException invalid) {
            return List.of(new Issue("rules", "RULE_REFERENCES_INVALID", "规则集合、独立/共享声明或版本内引用无效"));
        }
        var rules = TemplateRuleCollection.index(document.getRules());
        Map<String, TaskBusinessProviderRegistry.CompletionBinding> bindings = new LinkedHashMap<>();
        if (document.getStages() != null) document.getStages().stream().filter(Objects::nonNull).forEach(node ->
                bindings.put(node.getNodeKey(), binding(DeliveryDefinitionKind.STAGE, node.getWorkBinding())));
        if (document.getTasks() != null) document.getTasks().stream().filter(Objects::nonNull).forEach(node ->
                bindings.put(node.getNodeKey(), binding(DeliveryDefinitionKind.TASK, node.getWorkBinding())));
        Map<String, RuleProgram> programs = new LinkedHashMap<>();
        Set<String> creationFields = ProjectRuleFields.catalog().stream()
                .filter(ProjectRuleFields.Field::availableAtCreation).map(ProjectRuleFields.Field::code)
                .collect(Collectors.toUnmodifiableSet());
        for (VersionRule rule : rules.values()) {
            String path = "rules." + rule.key();
            boolean matching = Objects.equals(rule.key(), document.getMatchRuleKey());
            Set<String> fields = matching ? creationFields : ProjectRuleFields.codes();
            try {
                RuleProgram program = rule.kind() == VersionRule.Kind.CONDITION
                        ? compiler.compile(TemplateRuleCollection.condition(rules, rule.key())) : compiler.compileDecision(rule);
                programs.put(rule.key(), program);
                validateProgram(path, program, fields, matching, issues);
                for (var leaf : program.leaves()) {
                    if (!"BUSINESS_FACT".equals(leaf.predicate())) continue;
                    String source = leaf.parameters().path("sourceNodeKey").asText();
                    if (!source.isBlank() && (!bindings.containsKey(source)
                            || !businessProviders.supportsBoundCompletionFact(bindings.get(source), leaf.parameters().path("factCode").asText())))
                        issues.add(new Issue(path + "." + leaf.path(), "RULE_BUSINESS_SOURCE_UNAVAILABLE", "来源节点不存在或其业务绑定不支持所选事实"));
                    if (source.isBlank() && Objects.equals(rule.key(), document.getClosureRuleKey()))
                        issues.add(new Issue(path + "." + leaf.path(), "RULE_BUSINESS_SOURCE_REQUIRED", "项目收口的业务结果必须明确来源节点"));
                }
            } catch (RuntimeException invalid) {
                // Native expression errors can contain configured literals: return location, not raw engine messages.
                issues.add(new Issue(path, "RULE_EXECUTION_DEFINITION_INVALID", "规则或决策表执行定义无效，请检查表达式及字段绑定"));
            }
        }
        if (document.getStages() != null) for (int i = 0; i < document.getStages().size(); i++) {
            var node = document.getStages().get(i);
            if (node != null) validateNodeBindings("stages[" + i + "]", DeliveryDefinitionKind.STAGE,
                    node.getNodeKey(), node.getWorkBinding(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey(), programs, issues);
        }
        if (document.getTasks() != null) for (int i = 0; i < document.getTasks().size(); i++) {
            var node = document.getTasks().get(i);
            if (node != null) validateNodeBindings("tasks[" + i + "]", DeliveryDefinitionKind.TASK,
                    node.getNodeKey(), node.getWorkBinding(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey(), programs, issues);
        }
        if (document.getGates() == null) {
            issues.add(new Issue("gates", "RULE_EXECUTION_DEFINITION_INVALID", "门禁集合不能为空引用"));
            return List.copyOf(issues);
        }
        for (var gate : document.getGates()) {
            if (gate == null) {
                issues.add(new Issue("gates", "RULE_EXECUTION_DEFINITION_INVALID", "门禁定义不能为空"));
                continue;
            }
            String path = "gates." + gate.getNodeKey();
            try {
                var program = compiler.compileGateReferences(gate.getReferences());
                if (!LiteFlowChainELBuilder.validateWithEx(program.el()).isSuccess())
                    issues.add(new Issue(path, "RULE_COMPONENT_UNAVAILABLE", "门禁编排无效或执行组件未注册"));
            } catch (RuntimeException invalid) {
                issues.add(new Issue(path, "RULE_EXECUTION_DEFINITION_INVALID", "门禁引用执行定义无效"));
            }
        }
        return List.copyOf(issues);
    }

    private void validateProgram(String path, RuleProgram program, Set<String> fields, boolean matching,
                                 List<Issue> issues) {
        // Native parser resolves registered nodes, but does not run or register the generated chain.
        // API verified against LiteFlowChainELBuilder in liteflow-core 2.16.1 sources.
        if (!LiteFlowChainELBuilder.validateWithEx(program.el()).isSuccess())
            issues.add(new Issue(path, "RULE_COMPONENT_UNAVAILABLE", "规则编排无效或执行组件未注册"));
        for (RuleProgram.Leaf leaf : program.leaves()) validateLeaf(path, leaf, fields, matching, issues);
    }

    private static TaskBusinessProviderRegistry.CompletionBinding binding(DeliveryDefinitionKind kind, TemplateDesignerDocument.WorkBindingSpec binding) {
        return binding == null ? null : new TaskBusinessProviderRegistry.CompletionBinding(kind,
                binding.getType(), binding.getTargetContextCode(), binding.getTargetObjectType());
    }

    private void validateNodeBindings(String path, DeliveryDefinitionKind kind, String nodeKey, TemplateDesignerDocument.WorkBindingSpec binding,
                                      String admission, String completion, String exit, Map<String, RuleProgram> programs,
                                      List<Issue> issues) {
        var receiver = binding(kind, binding);
        String[] slots = {"admissionRuleKey", "completionRuleKey", "exitRuleKey"};
        String[] keys = {admission, completion, exit};
        for (int i = 0; i < keys.length; i++) {
            RuleProgram program = programs.get(keys[i]);
            if (program == null) continue; // Missing/invalid rules are reported by reference and compilation validation.
            for (RuleProgram.Leaf leaf : program.leaves()) {
                if (!"BUSINESS_FACT".equals(leaf.predicate())) continue;
                String source = leaf.parameters().path("sourceNodeKey").asText();
                if (i == 0 && (source.isBlank() || source.equals(nodeKey)))
                    issues.add(new Issue(path + "." + slots[i] + "." + leaf.path(), "RULE_BUSINESS_SOURCE_REQUIRED",
                            "准入业务条件必须选择其他来源节点，不能依赖尚未准入的本轮办理结果"));
                if (!source.isBlank()) continue; // Explicit sources are validated once against their own binding above.
                if (!businessProviders.supportsBoundCompletionFact(receiver, leaf.parameters().path("factCode").asText()))
                    issues.add(new Issue(path + "." + slots[i] + "." + leaf.path(), "RULE_BUSINESS_BINDING_UNAVAILABLE",
                            "规则所需业务事实与当前节点办理绑定或原模块节点能力不匹配"));
            }
        }
    }

    private void validateLeaf(String rulePath, RuleProgram.Leaf leaf, Set<String> fields, boolean matching,
                              List<Issue> issues) {
        String path = rulePath + "." + leaf.path();
        switch (leaf.predicate()) {
            case "FIELD" -> {
                String fieldCode = leaf.parameters().path("fieldCode").asText();
                if (!fields.contains(fieldCode)) {
                    issues.add(new Issue(path, "RULE_FIELD_UNAVAILABLE", "字段未开放或创建时不可用"));
                    return;
                }
                var field = ProjectRuleFields.catalog().stream().filter(item -> item.code().equals(fieldCode)).findFirst().orElseThrow();
                if (!field.valueType().equals(leaf.parameters().path("valueType").asText()))
                    issues.add(new Issue(path, "RULE_FIELD_TYPE_MISMATCH", "条件字段类型与开放字段目录不一致"));
            }
            case "DECISION", "DECISION_VALUE" -> {
                var table = JsonUtils.parseObject(leaf.parameters().path("table").toString(), DecisionTableDefinition.class);
                var metadata = decisions.validate(table, fields);
                if (leaf.predicate().equals("DECISION")
                        && !metadata.outputs().contains(leaf.parameters().path("fieldCode").asText()))
                    issues.add(new Issue(path, "DECISION_OUTPUT_UNAVAILABLE", "条件引用的输出列在决策表中不存在"));
            }
            case "CONSTANT" -> { }
            default -> {
                if (matching) issues.add(new Issue(path, "MATCH_REQUIRES_CREATION_FACTS", "模板匹配只能使用创建时字段，不能依赖尚未创建的节点或业务结果"));
                if ("BUSINESS_FACT".equals(leaf.predicate())
                        && !businessProviders.supportsCompletionFact(leaf.parameters().path("factCode").asText()))
                    issues.add(new Issue(path, "RULE_BUSINESS_FACT_UNAVAILABLE", "原模块未提供规则所需的业务事实"));
            }
        }
    }
}
