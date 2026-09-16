package com.dp.plat.lowcode.engine.rule;

import java.util.List;
import java.util.Map;

/**
 * 规则引擎服务。
 *
 * <p>支持三种规则类型：决策表 / 表达式 / LiteFlow。</p>
 */
public interface RuleEngineService {

    /**
     * 执行决策表，返回命中的行动作列表。
     *
     * @param definition 决策表 JSON 定义
     * @param facts      输入事实
     * @return 命中的行动作列表
     */
    List<Map<String, Object>> executeDecisionTable(String definition, Map<String, Object> facts);

    /**
     * 执行 Aviator 表达式，返回结果。
     *
     * @param expression Aviator 表达式
     * @param context    上下文变量
     * @return 表达式结果
     */
    Object executeExpression(String expression, Map<String, Object> context);

    /**
     * 执行 LiteFlow EL。
     *
     * @param el      LiteFlow EL 表达式
     * @param context 上下文变量
     * @return 执行结果
     */
    Object executeLiteFlow(String el, Map<String, Object> context);
}
