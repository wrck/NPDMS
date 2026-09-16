package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 条件分支节点执行器：执行 Groovy 布尔表达式，true 走 trueBranch，false 走 falseBranch。
 *
 * <p>节点 config: {expression: "布尔表达式", trueBranch: "nodeId", falseBranch: "nodeId"}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionExecutor implements MicroflowNodeExecutor {

    private final GroovySandboxExecutor groovySandboxExecutor;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.CONDITION;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String expression = (String) config.get("expression");
        if (expression == null) return null;

        Binding binding = new Binding(context.getVariables());
        Object result = groovySandboxExecutor.evaluate(binding, expression);
        boolean matched = Boolean.TRUE.equals(result);
        log.debug("ConditionExecutor: expression={}, result={}", expression, matched);
        return matched ? (String) config.get("trueBranch") : (String) config.get("falseBranch");
    }
}
