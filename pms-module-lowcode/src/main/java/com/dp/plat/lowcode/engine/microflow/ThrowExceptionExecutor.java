package com.dp.plat.lowcode.engine.microflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抛出异常节点执行器：抛出 {@link MicroflowExecutionException} 终止微流执行。
 *
 * <p>节点 config: {errorMessage: "错误信息", errorCode: "错误码"}</p>
 */
@Slf4j
@Component
public class ThrowExceptionExecutor implements MicroflowNodeExecutor {

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.THROW_EXCEPTION;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        String errorMessage = config != null ? (String) config.get("errorMessage") : null;
        String errorCode = config != null ? (String) config.get("errorCode") : null;
        log.warn("ThrowExceptionExecutor: code={}, message={}", errorCode, errorMessage);
        throw new MicroflowExecutionException(
                errorMessage == null ? "微流显式抛出异常" : errorMessage,
                errorCode);
    }
}
