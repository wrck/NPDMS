package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleDiagnostic;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.flowable.dmn.api.ExecuteDecisionContext;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.impl.el.ELInputEntryExpressionPreParser;
import org.flowable.dmn.engine.impl.el.ELOutputEntryExpressionPreParser;
import org.flowable.dmn.model.Decision;
import org.flowable.dmn.model.DecisionTable;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uses Flowable's own parser, expression pre-parsers and hit-policy executor, without deploying a second rule copy. */
@Service
@RequiredArgsConstructor
public class ProjectDecisionTableService {
    private final DmnEngine engine;

    public record Metadata(List<String> inputs, List<String> outputs, String hitPolicy) { }
    public record Result(boolean available, String reasonCode, List<Map<String, Object>> rows, List<RuleDiagnostic> diagnostics) { }

    public Metadata validate(DecisionTableDefinition source, Set<String> availableFields) {
        Parsed parsed = parse(source);
        for (String field : source.inputFields().values()) {
            if (!availableFields.contains(field)) throw new IllegalArgumentException("决策表引用未开放字段: " + field);
        }
        return parsed.metadata();
    }

    /** The same typed output selection is used by simulation and frozen runtime contracts. */
    public RuleFact resolve(Long tenantId, String versionRef, RuleProgram.Leaf leaf,
                            java.util.function.Function<String, RuleFact> facts) {
        var table = JsonUtils.convertObject(leaf.parameters().path("table"), DecisionTableDefinition.class);
        return select(evaluate(tenantId, versionRef, table, facts), leaf);
    }

    RuleFact select(Result decision, RuleProgram.Leaf leaf) {
        if (!decision.available()) return RuleFact.unknown(decision.reasonCode(), decision.diagnostics());
        if (leaf.predicate().equals("DECISION_VALUE"))
            return RuleFact.known(new DecisionRuleEvaluation.Values(decision.rows()));
        String output = leaf.parameters().path("fieldCode").asText();
        List<Object> selected = new ArrayList<>();
        for (var row : decision.rows()) {
            if (!row.containsKey(output)) return RuleFact.unknown("DECISION_OUTPUT_UNAVAILABLE");
            selected.add(row.get(output));
        }
        if (selected.isEmpty()) return RuleFact.unknown("DECISION_NO_RESULT");
        if (!leaf.parameters().path("quantifier").asText().isEmpty()) return RuleFact.known(selected);
        return selected.size() == 1 ? RuleFact.known(selected.getFirst()) : RuleFact.unknown("DECISION_QUANTIFIER_REQUIRED");
    }

    public Result evaluate(Long tenantId, String versionRef, DecisionTableDefinition source,
                           java.util.function.Function<String, RuleFact> facts) {
        Parsed parsed = parse(source);
        Map<String, Object> variables = new LinkedHashMap<>();
        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        for (String input : parsed.metadata().inputs()) {
            RuleFact fact = facts.apply(source.inputFields().get(input));
            if (fact == null || !fact.available()) {
                diagnostics.add(new RuleDiagnostic("rules." + source.key() + ".inputs." + input, "flowable-dmn",
                        fact == null ? "FACT_UNAVAILABLE" : fact.reasonCode(), source.inputFields().get(input)));
                continue;
            }
            Object value = fact.value();
            if (value != null && !(value instanceof String) && !(value instanceof Number)
                    && !(value instanceof Boolean) && !(value instanceof TemporalAccessor))
                return new Result(false, "DECISION_INPUT_TYPE_INVALID", List.of(), List.of(new RuleDiagnostic(
                        "rules." + source.key() + ".inputs." + input, "flowable-dmn", "DECISION_INPUT_TYPE_INVALID", source.inputFields().get(input))));
            variables.put(input, value instanceof TemporalAccessor ? value.toString() : value);
        }
        if (!diagnostics.isEmpty()) return new Result(false, "DECISION_INPUT_UNAVAILABLE", List.of(), List.copyOf(diagnostics));
        var context = new ExecuteDecisionContext();
        context.setTenantId(String.valueOf(tenantId));
        context.setDecisionKey(source.decisionKey());
        context.setDecisionId(versionRef + ":" + source.key());
        context.setDmnElement(parsed.decision());
        context.setVariables(variables);
        context.setDisableHistory(true);
        context.setFallbackToDefaultTenant(false);
        try {
            var configuration = engine.getDmnEngineConfiguration();
            var command = new org.flowable.common.engine.impl.interceptor.CommandConfig()
                    .setContextReusePossible(false).transactionNotSupported();
            var audit = configuration.getCommandExecutor().execute(command, commandContext ->
                    configuration.getRuleEngineExecutor().execute(parsed.decision(), context));
            if (Boolean.TRUE.equals(audit.isFailed())) return new Result(false, "DECISION_EXECUTION_FAILED", List.of(), List.of());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : audit.getDecisionResult())
                rows.add(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            if (rows.isEmpty()) return new Result(false, "DECISION_NO_RESULT", List.of(), List.of());
            return new Result(true, null, List.copyOf(rows), List.of());
        } catch (RuntimeException failed) {
            return new Result(false, "DECISION_EXECUTION_FAILED", List.of(), List.of());
        }
    }

    private Parsed parse(DecisionTableDefinition source) {
        if (source == null || source.key() == null || source.key().isBlank()
                || source.xml() == null || source.xml().isBlank())
            throw new IllegalArgumentException("决策表标识和DMN内容不能为空");
        byte[] xml = source.xml().getBytes(StandardCharsets.UTF_8);
        DmnDefinition model = new DmnXMLConverter().convertToDmnModel(() -> new ByteArrayInputStream(xml), true, true);
        Decision decision = model.getDecisionById(source.decisionKey());
        if (decision == null || !(decision.getExpression() instanceof DecisionTable table)
                || model.getDecisions().size() != 1 || !model.getDecisionServices().isEmpty())
            throw new IllegalArgumentException("每份决策表使用一个decisionTable；表间依赖在项目规则中显式配置");
        Set<String> inputNames = new HashSet<>();
        for (var input : table.getInputs()) {
            if (input.getInputExpression() == null) throw new IllegalArgumentException("决策表输入列缺少字段");
            String name = input.getInputExpression().getText();
            if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*") || !inputNames.add(name))
                throw new IllegalArgumentException("决策表输入必须使用唯一变量名");
        }
        if (!inputNames.equals(source.inputFields().keySet()))
            throw new IllegalArgumentException("决策表每个输入列必须绑定开放字段，不允许多余绑定");
        var expressions = engine.getDmnEngineConfiguration().getExpressionManager();
        for (var row : table.getRules()) {
            for (var cell : row.getInputEntries()) {
                String test = cell.getInputEntry().getText();
                // Flowable treats a blank cell or '-' as an unconditional input, before invoking its pre-parser.
                if (test == null || test.isEmpty() || "-".equals(test)) continue;
                var input = cell.getInputClause().getInputExpression();
                expressions.createExpression(ELInputEntryExpressionPreParser.parse(
                        test, input.getText(), input.getTypeRef()));
            }
            for (var cell : row.getOutputEntries()) {
                String value = cell.getOutputEntry().getText();
                if (value != null && !value.isEmpty())
                    expressions.createExpression(ELOutputEntryExpressionPreParser.parse(value));
            }
        }
        for (var output : table.getOutputs()) {
            if (output.getDefaultOutputEntry() != null)
                expressions.createExpression(ELOutputEntryExpressionPreParser.parse(output.getDefaultOutputEntry().getText()));
        }
        List<String> outputs = table.getOutputs().stream().map(output -> output.getName()).toList();
        if (outputs.isEmpty() || outputs.stream().anyMatch(name -> name == null || name.isBlank())
                || new HashSet<>(outputs).size() != outputs.size())
            throw new IllegalArgumentException("决策表输出列必须命名且唯一");
        if (table.getRules().isEmpty()) throw new IllegalArgumentException("决策表至少配置一行规则");
        return new Parsed(decision, new Metadata(inputNames.stream().sorted().toList(), outputs, table.getHitPolicy().name()));
    }

    private record Parsed(Decision decision, Metadata metadata) { }
}
