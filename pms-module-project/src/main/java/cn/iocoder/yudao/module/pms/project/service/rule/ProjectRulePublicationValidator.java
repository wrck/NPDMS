package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates execution dependencies without executing business facts or publishing a second rule source. */
@Component
@RequiredArgsConstructor
public class ProjectRulePublicationValidator {
    private final ProjectRuleCompiler compiler;
    private final ProjectDecisionTableService decisions;

    public List<Issue> validate(TemplateDesignerDocument submitted) {
        List<Issue> issues = new ArrayList<>();
        TemplateDesignerDocument document;
        try {
            document = TemplateRuleCollection.forEditing(submitted);
        } catch (IllegalArgumentException invalid) {
            return List.of(new Issue("rules", "RULE_REFERENCES_INVALID", "规则集合、独立/共享声明或版本内引用无效"));
        }
        var rules = TemplateRuleCollection.index(document.getRules());
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
                // Native parser resolves registered nodes, but does not run or register the generated chain.
                // API verified against LiteFlowChainELBuilder in liteflow-core 2.16.1 sources.
                if (!LiteFlowChainELBuilder.validateWithEx(program.el()).isSuccess())
                    issues.add(new Issue(path, "RULE_COMPONENT_UNAVAILABLE", "规则编排无效或执行组件未注册"));
                for (RuleProgram.Leaf leaf : program.leaves()) validateLeaf(path, leaf, fields, matching, issues);
            } catch (RuntimeException invalid) {
                // Native expression errors can contain configured literals: return location, not raw engine messages.
                issues.add(new Issue(path, "RULE_EXECUTION_DEFINITION_INVALID", "规则或决策表执行定义无效，请检查表达式及字段绑定"));
            }
        }
        return List.copyOf(issues);
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
            }
        }
    }
}
