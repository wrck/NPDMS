package com.dp.plat.lowcode.engine.rule;

import com.dp.plat.lowcode.engine.microflow.MicroflowEngine;
import com.dp.plat.lowcode.engine.microflow.MicroflowContext;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 规则集编排器（批次3-T1：规则集编排）。
 *
 * <p>将多个规则节点按编排语义（THEN/WHEN/IF/SWITCH）组合执行，
 * 实现"规则集编排"——借鉴 pms-rules 的 LiteFlow 能力，但不依赖 LiteFlow 动态节点注册，
 * 而是自实现编排语义以支持动态规则定义。</p>
 *
 * <p>编排语义：
 * <ul>
 *   <li>THEN：顺序执行，前一个节点的结果作为后一个节点的输入（key="input"）</li>
 *   <li>WHEN：并行执行所有节点，结果聚合为 Map（key=nodeId）</li>
 *   <li>IF：执行 conditionNode，结果为 true 时执行 thenNode，否则执行 elseNode</li>
 *   <li>SWITCH：执行 switchNode，结果值匹配 caseMapping 的 key 决定执行哪个节点</li>
 * </ul></p>
 *
 * <p>节点类型：decision_table / expression / liteflow / microflow。
 * 每种类型委托对应的执行器执行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSetOrchestrator {

    private final RuleEngineService ruleEngineService;
    private final LiteFlowExecutor liteFlowExecutor;
    private final MicroflowEngine microflowEngine;
    private final LowCodeMicroflowService microflowService;

    /**
     * 执行规则集。
     *
     * @param definition 规则集定义
     * @param inputs     输入参数
     * @return 执行结果（含 finalResult 和每个节点的结果 trace）
     */
    public RuleSetResult execute(RuleSetDefinition definition, Map<String, Object> inputs) {
        RuleSetResult result = new RuleSetResult();
        result.setCode(definition.getCode());
        result.setOrchestration(definition.getOrchestration());

        Map<String, Object> context = new HashMap<>();
        if (inputs != null) {
            context.putAll(inputs);
        }

        try {
            Map<String, RuleNodeResult> nodeResults = new ConcurrentHashMap<>();
            String orchestration = definition.getOrchestration() == null
                    ? "THEN" : definition.getOrchestration().toUpperCase();

            switch (orchestration) {
                case "THEN":
                    executeThen(definition, context, nodeResults);
                    break;
                case "WHEN":
                    executeWhen(definition, context, nodeResults);
                    break;
                case "IF":
                    executeIf(definition, context, nodeResults);
                    break;
                case "SWITCH":
                    executeSwitch(definition, context, nodeResults);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的编排语义: " + orchestration);
            }

            // 最终结果取上下文中的 "result" 键（最后一个执行节点写入）
            result.setFinalResult(context.get("result"));
            result.setNodeResults(new LinkedHashMap<>(nodeResults));
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("规则集 {} 执行失败: {}", definition.getCode(), e.getMessage(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setFinalResult(null);
        }

        return result;
    }

    /** THEN：顺序执行，前一个节点的结果作为后一个节点的输入 */
    private void executeThen(RuleSetDefinition def, Map<String, Object> context,
                             Map<String, RuleNodeResult> nodeResults) {
        if (def.getNodes() == null) return;
        for (RuleSetDefinition.RuleNode node : def.getNodes()) {
            // 将上一个节点的 result 作为当前节点的 input
            Object prevResult = context.get("result");
            if (prevResult != null) {
                context.put("input", prevResult);
            }
            Object nodeResult = executeNode(node, context);
            nodeResults.put(node.getId(), buildNodeResult(node, nodeResult, null));
            if (nodeResult != null) {
                context.put("result", nodeResult);
                context.put(node.getId(), nodeResult);
            }
        }
    }

    /** WHEN：并行执行所有节点，结果聚合为 Map */
    private void executeWhen(RuleSetDefinition def, Map<String, Object> context,
                             Map<String, RuleNodeResult> nodeResults) {
        if (def.getNodes() == null || def.getNodes().isEmpty()) return;

        List<CompletableFuture<Void>> futures = def.getNodes().stream()
                .map(node -> CompletableFuture.runAsync(() -> {
                    try {
                        // 每个并行节点用独立的上下文副本（避免并发修改）
                        Map<String, Object> nodeContext = new HashMap<>(context);
                        Object nodeResult = executeNode(node, nodeContext);
                        nodeResults.put(node.getId(), buildNodeResult(node, nodeResult, null));
                    } catch (Exception e) {
                        nodeResults.put(node.getId(), buildNodeResult(node, null, e));
                    }
                }))
                .collect(Collectors.toList());

        futures.forEach(CompletableFuture::join);

        // 聚合结果为 Map（key=nodeId）
        Map<String, Object> aggregated = new LinkedHashMap<>();
        nodeResults.forEach((id, nr) -> aggregated.put(id, nr.getResult()));
        context.put("result", aggregated);
    }

    /** IF：条件分支 */
    private void executeIf(RuleSetDefinition def, Map<String, Object> context,
                           Map<String, RuleNodeResult> nodeResults) {
        // 1. 执行条件节点
        RuleSetDefinition.RuleNode conditionNode = findNode(def, def.getConditionNodeId());
        Object conditionResult = executeNode(conditionNode, context);
        nodeResults.put(conditionNode.getId(), buildNodeResult(conditionNode, conditionResult, null));

        boolean condition = toBoolean(conditionResult);
        log.debug("IF 条件节点 {} 结果: {} → {}", conditionNode.getId(), conditionResult, condition);

        // 2. 根据条件执行 then 或 else
        String targetNodeId = condition ? def.getThenNodeId() : def.getElseNodeId();
        if (targetNodeId != null) {
            RuleSetDefinition.RuleNode targetNode = findNode(def, targetNodeId);
            Object targetResult = executeNode(targetNode, context);
            nodeResults.put(targetNode.getId(), buildNodeResult(targetNode, targetResult, null));
            if (targetResult != null) {
                context.put("result", targetResult);
            }
        }
    }

    /** SWITCH：选择分支 */
    private void executeSwitch(RuleSetDefinition def, Map<String, Object> context,
                               Map<String, RuleNodeResult> nodeResults) {
        // 1. 执行 switch 节点
        RuleSetDefinition.RuleNode switchNode = findNode(def, def.getSwitchNodeId());
        Object switchResult = executeNode(switchNode, context);
        nodeResults.put(switchNode.getId(), buildNodeResult(switchNode, switchResult, null));

        String switchValue = switchResult == null ? "null" : switchResult.toString();
        log.debug("SWITCH 节点 {} 结果: {}", switchNode.getId(), switchValue);

        // 2. 匹配 case
        String targetNodeId = null;
        if (def.getCaseMapping() != null) {
            targetNodeId = def.getCaseMapping().get(switchValue);
        }
        if (targetNodeId == null && def.getDefaultNodeId() != null) {
            targetNodeId = def.getDefaultNodeId();
            log.debug("SWITCH 无匹配 case，使用默认节点: {}", targetNodeId);
        }

        // 3. 执行目标节点
        if (targetNodeId != null) {
            RuleSetDefinition.RuleNode targetNode = findNode(def, targetNodeId);
            Object targetResult = executeNode(targetNode, context);
            nodeResults.put(targetNode.getId(), buildNodeResult(targetNode, targetResult, null));
            if (targetResult != null) {
                context.put("result", targetResult);
            }
        }
    }

    /** 执行单个规则节点 */
    @SuppressWarnings("unchecked")
    private Object executeNode(RuleSetDefinition.RuleNode node, Map<String, Object> context) {
        log.debug("执行规则节点: id={}, type={}", node.getId(), node.getType());
        String type = node.getType();
        if (type == null) {
            throw new IllegalArgumentException("节点 " + node.getId() + " 未指定 type");
        }

        switch (type) {
            case "decision_table": {
                Map<String, Object> facts = new HashMap<>(context);
                List<Map<String, Object>> actions = ruleEngineService.executeDecisionTable(
                        node.getDefinition(), facts);
                return actions;
            }
            case "expression": {
                return ruleEngineService.executeExpression(node.getExpression(), context);
            }
            case "liteflow": {
                return liteFlowExecutor.execute(node.getEl(), context);
            }
            case "microflow": {
                return executeMicroflowNode(node, context);
            }
            default:
                throw new IllegalArgumentException("不支持的节点类型: " + type);
        }
    }

    /** 执行微流节点 */
    @SuppressWarnings("unchecked")
    private Object executeMicroflowNode(RuleSetDefinition.RuleNode node, Map<String, Object> context) {
        String definitionJson = node.getMicroflowDefinition();

        // 如果未直接提供定义，从数据库加载
        if (definitionJson == null) {
            LowCodeMicroflow microflow = null;
            if (node.getMicroflowId() != null) {
                microflow = microflowService.getById(node.getMicroflowId());
            } else if (node.getMicroflowCode() != null) {
                microflow = microflowService.lambdaQuery()
                        .eq(LowCodeMicroflow::getCode, node.getMicroflowCode())
                        .one();
            }
            if (microflow == null) {
                throw new IllegalArgumentException("微流不存在: id=" + node.getMicroflowId()
                        + ", code=" + node.getMicroflowCode());
            }
            definitionJson = microflow.getDefinition();
        }

        MicroflowContext mfContext = microflowEngine.execute(
                node.getMicroflowId(), node.getMicroflowCode(), definitionJson, context);
        return mfContext.getResult();
    }

    /** 查找节点 */
    private RuleSetDefinition.RuleNode findNode(RuleSetDefinition def, String nodeId) {
        if (def.getNodes() == null) return null;
        return def.getNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));
    }

    /** 转换为 boolean */
    private boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        String s = value.toString();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    /** 构建节点结果 */
    private RuleNodeResult buildNodeResult(RuleSetDefinition.RuleNode node,
                                           Object result, Exception error) {
        RuleNodeResult nr = new RuleNodeResult();
        nr.setNodeId(node.getId());
        nr.setNodeType(node.getType());
        nr.setResult(result);
        if (error != null) {
            nr.setStatus("FAILED");
            nr.setError(error.getMessage());
        } else {
            nr.setStatus("SUCCESS");
        }
        return nr;
    }

    /**
     * 规则集执行结果。
     */
    @lombok.Data
    public static class RuleSetResult {
        private String code;
        private String orchestration;
        private String status;
        private Object finalResult;
        private Map<String, RuleNodeResult> nodeResults;
        private String errorMessage;
    }

    /**
     * 单个节点执行结果。
     */
    @lombok.Data
    public static class RuleNodeResult {
        private String nodeId;
        private String nodeType;
        private String status;
        private Object result;
        private String error;
    }
}
