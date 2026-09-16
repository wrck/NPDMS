package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 调用子微流节点执行器：通过 {@link LowCodeMicroflowService} 执行指定微流，结果存入 variables["_microflowResult"]。
 *
 * <p>节点 config: {microflowCode: "微流编码", inputsExpression: "Groovy 表达式（返回 Map）"}</p>
 *
 * <p>使用 {@code @Lazy} 注入服务以打破与 LowCodeMicroflowServiceImpl 的循环依赖
 * （MicroflowEngine → executors → 本执行器 → LowCodeMicroflowService → MicroflowEngine）。</p>
 */
@Slf4j
@Component
public class CallMicroflowExecutor implements MicroflowNodeExecutor {

    private final GroovySandboxExecutor groovySandboxExecutor;
    private final LowCodeMicroflowService lowCodeMicroflowService;

    public CallMicroflowExecutor(GroovySandboxExecutor groovySandboxExecutor,
                                 @Lazy LowCodeMicroflowService lowCodeMicroflowService) {
        this.groovySandboxExecutor = groovySandboxExecutor;
        this.lowCodeMicroflowService = lowCodeMicroflowService;
    }

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.CALL_MICROFLOW;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String microflowCode = (String) config.get("microflowCode");
        if (microflowCode == null) return null;

        Map<String, Object> inputs = evaluateInputs(config.get("inputsExpression"), context);
        Map<String, Object> result = lowCodeMicroflowService.execute(microflowCode, inputs);
        context.setVariable("_microflowResult", result);
        log.debug("CallMicroflowExecutor: {} = {}", microflowCode, result);
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
