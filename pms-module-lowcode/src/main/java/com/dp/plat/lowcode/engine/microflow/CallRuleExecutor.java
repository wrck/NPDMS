package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 调用规则节点执行器：通过 {@link LowCodeRuleService} 执行指定规则，结果存入 variables["_ruleResult"]。
 *
 * <p>节点 config: {ruleCode: "规则编码", inputsExpression: "Groovy 表达式（返回 Map）"}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallRuleExecutor implements MicroflowNodeExecutor {

    private final GroovySandboxExecutor groovySandboxExecutor;
    private final LowCodeRuleService lowCodeRuleService;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.CALL_RULE;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String ruleCode = (String) config.get("ruleCode");
        if (ruleCode == null) return null;

        Map<String, Object> inputs = evaluateInputs(config.get("inputsExpression"), context);
        Map<String, Object> result = lowCodeRuleService.execute(ruleCode, inputs);
        context.setVariable("_ruleResult", result);
        log.debug("CallRuleExecutor: {} = {}", ruleCode, result);
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateInputs(Object inputsExpression, MicroflowContext context) {
        if (inputsExpression == null || inputsExpression.toString().isBlank()) {
            return Collections.emptyMap();
        }
        Binding binding = new Binding(context.getVariables());
        Object value = groovySandboxExecutor.evaluate(binding, inputsExpression.toString());
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }
}
