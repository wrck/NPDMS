package com.dp.plat.lowcode.engine.microflow;

import java.util.Map;

/**
 * 微流节点执行器接口。
 *
 * <p>每种节点类型对应一个执行器实现。返回下一个要执行的节点 ID（null 表示无后续或主流程结束）。</p>
 */
public interface MicroflowNodeExecutor {

    /**
     * 节点类型
     */
    MicroflowNodeType getNodeType();

    /**
     * 执行节点逻辑。
     *
     * @param nodeDef  节点定义 JSON（含 id, type, config 等字段）
     * @param context  执行上下文
     * @return 下一节点 ID；null 表示按默认顺序或终止
     */
    String execute(Map<String, Object> nodeDef, MicroflowContext context);
}
