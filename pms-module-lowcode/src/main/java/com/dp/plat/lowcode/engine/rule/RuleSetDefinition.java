package com.dp.plat.lowcode.engine.rule;

import lombok.Data;

import java.util.List;
import java.util.Map;


/**
 * 规则集定义（批次3-T1：规则集编排）。
 *
 * <p>一个规则集由多个规则节点 + 编排语义组成，支持：</p>
 * <ul>
 *   <li>THEN（顺序）：节点按顺序执行，前一个的结果作为后一个的输入</li>
 *   <li>WHEN（并行）：节点并行执行，等待全部完成</li>
 *   <li>IF（条件）：conditionNode 结果为 true 时执行 thenNode，否则执行 elseNode</li>
 *   <li>SWITCH（选择）：switchNode 结果决定执行哪个 case 节点</li>
 * </ul>
 *
 * <p>规则节点类型：</p>
 * <ul>
 *   <li>decision_table：决策表（definition + facts → actions）</li>
 *   <li>expression：Aviator 表达式（expression + context → result）</li>
 *   <li>liteflow：LiteFlow EL（el + context → result）</li>
 *   <li>microflow：微流（microflowCode + inputs → result）</li>
 * </ul>
 */
@Data
public class RuleSetDefinition {

    /** 规则集编码 */
    private String code;

    /** 规则集名称 */
    private String name;

    /** 编排语义：THEN | WHEN | IF | SWITCH */
    private String orchestration = "THEN";

    /** 规则节点列表（THEN/WHEN 模式使用） */
    private List<RuleNode> nodes;

    /** 条件节点 ID（IF 模式：条件判断节点） */
    private String conditionNodeId;

    /** then 节点 ID（IF 模式：条件为 true 时执行） */
    private String thenNodeId;

    /** else 节点 ID（IF 模式：条件为 false 时执行，可选） */
    private String elseNodeId;

    /** switch 节点 ID（SWITCH 模式：选择判断节点） */
    private String switchNodeId;

    /** case 映射（SWITCH 模式：switchValue → nodeIds） */
    private Map<String, String> caseMapping;

    /** 默认节点 ID（SWITCH 模式：无匹配 case 时执行，可选） */
    private String defaultNodeId;

    /**
     * 规则节点定义。
     */
    @Data
    public static class RuleNode {
        /** 节点 ID（在编排中引用） */
        private String id;
        /** 节点类型：decision_table | expression | liteflow | microflow */
        private String type;
        /** 节点名称（描述用） */
        private String name;
        /** 决策表定义 JSON（type=decision_table 时使用） */
        private String definition;
        /** Aviator 表达式（type=expression 时使用） */
        private String expression;
        /** LiteFlow EL（type=liteflow 时使用） */
        private String el;
        /** 微流编码（type=microflow 时使用） */
        private String microflowCode;
        /** 微流 ID（type=microflow 时使用，优先于 microflowCode） */
        private Long microflowId;
        /** 微流定义 JSON（type=microflow 时直接使用，绕过数据库查询） */
        private String microflowDefinition;
    }
}
