package com.dp.plat.lowcode.engine.microflow;

import com.dp.plat.lowcode.engine.apm.LowCodeApmService;
import com.dp.plat.lowcode.entity.LowCodeMicroflowExecutionLog;
import com.dp.plat.lowcode.mapper.LowCodeMicroflowExecutionLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 微流执行引擎。
 *
 * <p>遍历微流定义中的 DAG 节点，按节点类型分发到对应的 NodeExecutor 执行。
 * 支持顺序执行 + 条件跳转 + 提前终止（RETURN/END）。</p>
 *
 * <p>执行每个节点前后记录执行轨迹（借鉴 Joget APM），通过 executionId 串联同一次执行的所有节点轨迹。
 * 轨迹记录为 best-effort，不影响主流程执行。</p>
 *
 * <p><b>APM 指标</b>（批次5-T9）：整体执行与单节点执行均通过 {@link LowCodeApmService} 记录
 * Micrometer 指标（Counter + Timer），供 Prometheus 采集与 Grafana 面板展示。
 * APM 记录为 best-effort，{@link LowCodeApmService} 未注入时 no-op。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicroflowEngine {

    private final ObjectMapper objectMapper;
    private final List<MicroflowNodeExecutor> executors;
    private final LowCodeMicroflowExecutionLogMapper executionLogMapper;

    /** APM 指标服务（可选注入，未注入时 no-op，不影响微流执行） */
    @Autowired(required = false)
    private LowCodeApmService apmService;

    /**
     * 执行微流。
     *
     * @param microflowId    微流ID（用于轨迹记录，可为 null）
     * @param microflowCode  微流编码（用于轨迹记录，可为 null）
     * @param definitionJson 微流定义 JSON（含 nodes: [{id, type, config}], edges: [{source, target}]）
     * @param inputs         输入参数
     * @return 执行上下文（含 result）
     */
    @SuppressWarnings("unchecked")
    public MicroflowContext execute(Long microflowId, String microflowCode,
                                    String definitionJson, Map<String, Object> inputs) {
        String executionId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        boolean success = true;
        try {
            Map<String, Object> definition = objectMapper.readValue(definitionJson,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

            // 构建节点查找表
            Map<String, Map<String, Object>> nodeMap = nodes.stream()
                    .collect(Collectors.toMap(n -> (String) n.get("id"), n -> n, (a, b) -> a, HashMap::new));

            // 构建边查找表 source → target
            Map<String, String> edgeMap = edges.stream()
                    .collect(Collectors.toMap(e -> (String) e.get("source"), e -> (String) e.get("target"), (a, b) -> a));

            // 找 START 节点
            String currentNodeId = nodes.stream()
                    .filter(n -> "START".equals(n.get("type")))
                    .map(n -> (String) n.get("id"))
                    .findFirst()
                    .orElse(nodes.isEmpty() ? null : (String) nodes.get(0).get("id"));

            MicroflowContext context = new MicroflowContext(inputs);

            // 循环执行
            int safetyCounter = 0;
            while (currentNodeId != null && !context.isTerminated() && safetyCounter++ < 1000) {
                Map<String, Object> node = nodeMap.get(currentNodeId);
                if (node == null) break;

                String type = (String) node.get("type");
                MicroflowNodeType nodeType = MicroflowNodeType.valueOf(type);
                MicroflowNodeExecutor executor = findExecutor(nodeType);

                String nextNodeId = executeNodeWithTrace(executionId, microflowId, microflowCode,
                        node, nodeType, executor, context);
                // 如果执行器未指定下一节点，按默认边走
                if (nextNodeId == null && !context.isTerminated()) {
                    nextNodeId = edgeMap.get(currentNodeId);
                }
                currentNodeId = nextNodeId;
            }
            return context;
        } catch (MicroflowExecutionException e) {
            success = false;
            throw e;
        } catch (Exception e) {
            success = false;
            throw new RuntimeException("微流执行失败", e);
        } finally {
            // APM 指标记录（best-effort，apmService 可能为 null）
            if (apmService != null) {
                apmService.recordMicroflowExecution(microflowCode,
                        System.currentTimeMillis() - startMs, success);
            }
        }
    }

    /**
     * 执行单个节点（调试模式用）。
     *
     * <p>解析微流定义，定位指定节点并执行，返回执行结果与下一节点 ID。
     * 不循环、不写执行轨迹（调试步骤不应污染生产执行日志表）。
     * 执行器返回 null 时按默认出边推导下一节点，与 {@link #execute} 保持一致。</p>
     *
     * @param definitionJson 微流定义 JSON（含 nodes / edges）
     * @param variables      当前变量状态（执行后由结果快照替换）
     * @param nodeId         要执行的节点 ID
     * @return 节点执行结果（含下一节点 ID 与最新变量）
     */
    @SuppressWarnings("unchecked")
    public MicroflowDebugger.DebugStepResult executeStep(String definitionJson,
                                                         Map<String, Object> variables,
                                                         String nodeId) {
        try {
            Map<String, Object> definition = objectMapper.readValue(definitionJson,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

            // 构建边查找表 source → target（与 execute 一致）
            Map<String, String> edgeMap = edges == null
                    ? new HashMap<>()
                    : edges.stream().collect(Collectors.toMap(
                            e -> (String) e.get("source"),
                            e -> (String) e.get("target"),
                            (a, b) -> a));

            Map<String, Object> node = nodes == null ? null : nodes.stream()
                    .filter(n -> nodeId.equals(n.get("id")))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                throw new RuntimeException("节点不存在: " + nodeId);
            }

            String type = (String) node.get("type");
            MicroflowNodeType nodeType = MicroflowNodeType.valueOf(type);
            MicroflowNodeExecutor executor = findExecutor(nodeType);

            // 以当前变量快照构造上下文（MicroflowContext 构造时会将入参复制到 variables）
            MicroflowContext context = new MicroflowContext(variables);
            String nextNodeId = executor != null ? executor.execute(node, context) : null;
            // 执行器未指定下一节点且未终止 → 按默认边走
            if (nextNodeId == null && !context.isTerminated()) {
                nextNodeId = edgeMap.get(nodeId);
            }

            MicroflowDebugger.DebugStepResult result = new MicroflowDebugger.DebugStepResult();
            result.setNodeId(nodeId);
            result.setNodeType(type);
            result.setVariables(context.getVariables());
            result.setResult(context.getResult());
            result.setNextNodeId(nextNodeId);
            // 终止（RETURN/END）或无后续节点 → COMPLETED；否则 PAUSED
            result.setStatus(context.isTerminated() || nextNodeId == null ? "COMPLETED" : "PAUSED");
            return result;
        } catch (MicroflowExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("微流单步执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行单个节点并记录轨迹（best-effort）。
     */
    private String executeNodeWithTrace(String executionId, Long microflowId, String microflowCode,
                                        Map<String, Object> node, MicroflowNodeType nodeType,
                                        MicroflowNodeExecutor executor, MicroflowContext context) {
        String nodeId = (String) node.get("id");
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();

        LowCodeMicroflowExecutionLog logEntry = LowCodeMicroflowExecutionLog.builder()
                .microflowId(microflowId)
                .microflowCode(microflowCode == null ? "unknown" : microflowCode)
                .executionId(executionId)
                .nodeId(nodeId)
                .nodeType(nodeType.name())
                .startTime(startTime)
                .inputs(toJson(node.get("config")))
                .variablesSnapshot(toJson(context.getVariables()))
                .status("RUNNING")
                .operator(currentOperator())
                .build();
        insertLog(logEntry);

        try {
            String nextNodeId = executor != null ? executor.execute(node, context) : null;
            long durationMs = System.currentTimeMillis() - startMs;
            logEntry.setEndTime(LocalDateTime.now());
            logEntry.setDurationMs(durationMs);
            logEntry.setOutputs(context.getResult() != null ? toJson(context.getResult()) : null);
            logEntry.setStatus("SUCCESS");
            updateLog(logEntry);
            // APM 节点级指标记录（best-effort）
            if (apmService != null) {
                apmService.recordMicroflowNodeExecution(nodeType.name(), durationMs, true);
            }
            return nextNodeId;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            logEntry.setEndTime(LocalDateTime.now());
            logEntry.setDurationMs(durationMs);
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage(e.getMessage());
            updateLog(logEntry);
            // APM 节点级指标记录（best-effort）
            if (apmService != null) {
                apmService.recordMicroflowNodeExecution(nodeType.name(), durationMs, false);
            }
            throw e;
        }
    }

    private MicroflowNodeExecutor findExecutor(MicroflowNodeType type) {
        return executors.stream()
                .filter(e -> e.getNodeType() == type)
                .findFirst()
                .orElse(null);
    }

    /** 序列化为 JSON 字符串（best-effort，失败返回 null） */
    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化执行轨迹字段失败: {}", e.getMessage());
            return null;
        }
    }

    /** 插入轨迹记录（best-effort） */
    private void insertLog(LowCodeMicroflowExecutionLog logEntry) {
        try {
            if (executionLogMapper != null) {
                executionLogMapper.insert(logEntry);
            }
        } catch (Exception e) {
            log.warn("写入微流执行轨迹失败: {}", e.getMessage());
        }
    }

    /** 更新轨迹记录（best-effort） */
    private void updateLog(LowCodeMicroflowExecutionLog logEntry) {
        try {
            if (executionLogMapper != null && logEntry.getId() != null) {
                executionLogMapper.updateById(logEntry);
            }
        } catch (Exception e) {
            log.warn("更新微流执行轨迹失败: {}", e.getMessage());
        }
    }

    /** 获取当前操作人（best-effort，无安全上下文时返回 null） */
    private String currentOperator() {
        try {
            Object principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            return principal == null ? null : principal.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
