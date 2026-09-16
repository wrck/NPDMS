package com.dp.plat.lowcode.engine.microflow;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * START / END 节点执行器：无操作，仅做边界标记。
 */
@Component
public class StartEndExecutor implements MicroflowNodeExecutor {

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.START;
    }

    /**
     * 同时支持 START 和 END 节点：END 节点终止流程。
     */
    public boolean supportsEnd() {
        return true;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        String type = (String) nodeDef.get("type");
        if ("END".equals(type)) {
            context.setTerminated(true);
        }
        return null;
    }
}
