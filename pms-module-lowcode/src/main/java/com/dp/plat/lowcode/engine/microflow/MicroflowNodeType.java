package com.dp.plat.lowcode.engine.microflow;

/**
 * 微流节点类型枚举。
 */
public enum MicroflowNodeType {
    /** 开始节点 */
    START,
    /** 结束节点 */
    END,
    /** 赋值节点（Groovy 表达式） */
    ASSIGN,
    /** 条件分支节点（Groovy 布尔表达式） */
    CONDITION,
    /** 循环节点（Groovy 布尔表达式） */
    LOOP,
    /** 调用 Spring 服务 */
    CALL_SERVICE,
    /** 调用另一个微流 */
    CALL_MICROFLOW,
    /** 调用规则 */
    CALL_RULE,
    /** 调用连接器 */
    CALL_CONNECTOR,
    /** 抛出异常 */
    THROW_EXCEPTION,
    /** 返回结果 */
    RETURN
}
