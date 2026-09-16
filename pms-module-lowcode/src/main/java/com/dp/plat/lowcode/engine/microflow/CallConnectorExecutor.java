package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import com.dp.plat.lowcode.engine.connector.ConnectorResult;
import com.dp.plat.lowcode.service.LowCodeConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 调用连接器节点执行器：通过 {@link LowCodeConnectorService} 执行指定连接器，结果存入 variables["_connectorResult"]。
 *
 * <p>节点 config: {connectorCode: "连接器编码", inputsExpression: "Groovy 表达式（返回 Map）"}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallConnectorExecutor implements MicroflowNodeExecutor {

    private final GroovySandboxExecutor groovySandboxExecutor;
    private final LowCodeConnectorService lowCodeConnectorService;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.CALL_CONNECTOR;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String connectorCode = (String) config.get("connectorCode");
        if (connectorCode == null) return null;

        Map<String, Object> inputs = evaluateInputs(config.get("inputsExpression"), context);
        ConnectorResult result = lowCodeConnectorService.execute(connectorCode, inputs);
        context.setVariable("_connectorResult", result);
        log.debug("CallConnectorExecutor: {} success={}", connectorCode, result.isSuccess());
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
