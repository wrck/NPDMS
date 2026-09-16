package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 赋值节点执行器：使用 Groovy 沙箱执行表达式，结果写入 variables。
 *
 * <p>节点 config: {target: "变量名", expression: "Groovy 表达式"}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignExecutor implements MicroflowNodeExecutor {

    private final GroovySandboxExecutor groovySandboxExecutor;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.ASSIGN;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String target = (String) config.get("target");
        String expression = (String) config.get("expression");
        if (target == null || expression == null) return null;

        Binding binding = new Binding(context.getVariables());
        Object value = groovySandboxExecutor.evaluate(binding, expression);
        context.setVariable(target, value);
        log.debug("AssignExecutor: {} = {}", target, value);
        return null;
    }
}
