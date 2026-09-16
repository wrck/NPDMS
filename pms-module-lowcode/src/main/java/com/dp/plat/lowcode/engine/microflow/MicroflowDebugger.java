package com.dp.plat.lowcode.engine.microflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微流断点调试器（借鉴 Mendix Microflows 调试器）。
 *
 * <p>支持：断点设置、单步执行（step over）、继续执行（continue）、变量监视。
 * 调试会话通过 debugSessionId 隔离，多用户可同时调试不同微流。</p>
 *
 * <p>会话存储使用 {@link ConcurrentHashMap}，支持多用户并发调试。
 * 每个会话的单步/继续操作以会话对象为锁串行化，避免同一会话并发推进导致状态错乱。
 * 会话设 30 分钟无操作超时，由 {@link #evictExpired()} 定时清理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MicroflowDebugger {

    /** 调试会话超时：30 分钟无操作自动清理 */
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L;
    /** continueExecution 单次最大步数，防止死循环 */
    private static final int MAX_CONTINUE_STEPS = 1000;

    private final ObjectMapper objectMapper;
    private final LowCodeMicroflowService microflowService;
    private final MicroflowEngine microflowEngine;

    /** debugSessionId → DebugSession */
    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();

    /**
     * 启动调试会话。
     *
     * <p>根据微流编码加载微流定义，定位 START 节点作为起始 currentNodeId，
     * 将输入参数注入初始变量作用域，并按传入断点集合初始化断点列表。</p>
     *
     * @param microflowCode      微流编码
     * @param inputs             输入参数
     * @param breakpointNodeIds  初始断点节点 ID 集合（可为 null）
     * @return 调试会话
     */
    public DebugSession startSession(String microflowCode, Map<String, Object> inputs,
                                     Set<String> breakpointNodeIds) {
        LowCodeMicroflow microflow = microflowService.getOne(new LambdaQueryWrapper<LowCodeMicroflow>()
                .eq(LowCodeMicroflow::getCode, microflowCode));
        if (microflow == null) {
            throw new RuntimeException("微流不存在: " + microflowCode);
        }
        String definitionJson = microflow.getDefinition();
        String startNodeId = findStartNodeId(definitionJson);

        String sessionId = UUID.randomUUID().toString();
        Set<String> breakpoints = ConcurrentHashMap.newKeySet();
        if (breakpointNodeIds != null) {
            breakpoints.addAll(breakpointNodeIds);
        }
        Map<String, Object> variables = new HashMap<>();
        if (inputs != null) {
            variables.putAll(inputs);
        }

        DebugSession session = new DebugSession();
        session.setSessionId(sessionId);
        session.setMicroflowCode(microflowCode);
        session.setMicroflowId(microflow.getId());
        session.setDefinitionJson(definitionJson);
        session.setInputs(variables);
        session.setBreakpointNodeIds(breakpoints);
        session.setVariables(variables);
        session.setCurrentNodeId(startNodeId);
        session.setResult(null);
        session.setTerminated(false);
        session.setLastActivityTime(System.currentTimeMillis());

        sessions.put(sessionId, session);
        log.info("启动微流调试会话: microflow={}, session={}, breakpoints={}",
                microflowCode, sessionId, breakpoints);
        return session;
    }

    /**
     * 单步执行：执行当前节点并暂停于下一节点。
     *
     * @param sessionId 调试会话 ID
     * @return 本步执行结果（含执行后变量与下一节点 ID）
     */
    public DebugStepResult stepOver(String sessionId) {
        DebugSession session = requireSession(sessionId);
        synchronized (session) {
            if (session.isTerminated()) {
                return completedResult(session);
            }
            session.setLastActivityTime(System.currentTimeMillis());
            try {
                DebugStepResult result = microflowEngine.executeStep(
                        session.getDefinitionJson(), session.getVariables(), session.getCurrentNodeId());
                applyStep(session, result);
                return result;
            } catch (Exception e) {
                return failSession(session, e);
            }
        }
    }

    /**
     * 继续执行：循环执行节点，直到命中断点、微流结束或失败。
     *
     * @param sessionId 调试会话 ID
     * @return 最后一步执行结果
     */
    public DebugStepResult continueExecution(String sessionId) {
        DebugSession session = requireSession(sessionId);
        synchronized (session) {
            if (session.isTerminated()) {
                return completedResult(session);
            }
            session.setLastActivityTime(System.currentTimeMillis());
            DebugStepResult last = null;
            int steps = 0;
            while (!session.isTerminated() && steps++ < MAX_CONTINUE_STEPS) {
                try {
                    DebugStepResult result = microflowEngine.executeStep(
                            session.getDefinitionJson(), session.getVariables(), session.getCurrentNodeId());
                    applyStep(session, result);
                    last = result;
                    // 微流结束或失败 → 终止
                    if ("COMPLETED".equals(result.getStatus()) || "FAILED".equals(result.getStatus())) {
                        break;
                    }
                    // 命中下一断点 → 暂停
                    if (result.getNextNodeId() != null
                            && session.getBreakpointNodeIds().contains(result.getNextNodeId())) {
                        result.setStatus("PAUSED");
                        break;
                    }
                } catch (Exception e) {
                    return failSession(session, e);
                }
            }
            if (last == null) {
                last = completedResult(session);
            }
            return last;
        }
    }

    /** 获取当前变量状态（返回副本，避免外部修改污染会话） */
    public Map<String, Object> getVariables(String sessionId) {
        DebugSession session = sessions.get(sessionId);
        if (session == null) return Collections.emptyMap();
        synchronized (session) {
            return new HashMap<>(session.getVariables());
        }
    }

    /** 添加断点 */
    public void addBreakpoint(String sessionId, String nodeId) {
        DebugSession session = sessions.get(sessionId);
        if (session != null && nodeId != null) {
            session.getBreakpointNodeIds().add(nodeId);
        }
    }

    /** 移除断点 */
    public void removeBreakpoint(String sessionId, String nodeId) {
        DebugSession session = sessions.get(sessionId);
        if (session != null && nodeId != null) {
            session.getBreakpointNodeIds().remove(nodeId);
        }
    }

    /** 终止调试会话 */
    public void terminate(String sessionId) {
        DebugSession session = sessions.remove(sessionId);
        if (session != null) {
            synchronized (session) {
                session.setTerminated(true);
            }
            log.info("终止微流调试会话: session={}", sessionId);
        }
    }

    /**
     * 定时清理超时会话（每分钟一次）。
     *
     * <p>需 {@code @EnableScheduling} 支持（pms-admin 已启用）。
     * 即使调度未生效，{@link #requireSession} 等访问路径也不主动清理，
     * 超时会话仅占用少量内存，不影响功能正确性。</p>
     */
    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> now - e.getValue().getLastActivityTime() > SESSION_TIMEOUT_MS);
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("清理超时微流调试会话: count={}", removed);
        }
    }

    // ===================== 内部辅助 =====================

    private DebugSession requireSession(String sessionId) {
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeException("调试会话不存在或已超时: " + sessionId);
        }
        return session;
    }

    /** 将单步结果应用到会话状态 */
    private void applyStep(DebugSession session, DebugStepResult result) {
        session.setVariables(result.getVariables());
        session.setCurrentNodeId(result.getNextNodeId());
        session.setResult(result.getResult());
        if ("COMPLETED".equals(result.getStatus()) || "FAILED".equals(result.getStatus())) {
            session.setTerminated(true);
        }
    }

    /** 标记会话失败并返回失败结果 */
    private DebugStepResult failSession(DebugSession session, Exception e) {
        session.setTerminated(true);
        DebugStepResult fail = new DebugStepResult();
        fail.setNodeId(session.getCurrentNodeId());
        fail.setStatus("FAILED");
        fail.setVariables(new HashMap<>(session.getVariables()));
        fail.setResult(session.getResult());
        fail.setNextNodeId(null);
        fail.setErrorMessage(e.getMessage());
        log.warn("微流调试执行失败: session={}, node={}", session.getSessionId(), session.getCurrentNodeId(), e);
        return fail;
    }

    /** 构造已完成结果（会话已终止时返回） */
    private DebugStepResult completedResult(DebugSession session) {
        DebugStepResult r = new DebugStepResult();
        r.setNodeId(session.getCurrentNodeId());
        r.setStatus("COMPLETED");
        r.setVariables(new HashMap<>(session.getVariables()));
        r.setResult(session.getResult());
        r.setNextNodeId(null);
        return r;
    }

    /** 解析微流定义，定位 START 节点 ID（缺失时回退到首节点） */
    @SuppressWarnings("unchecked")
    private String findStartNodeId(String definitionJson) {
        try {
            Map<String, Object> definition = objectMapper.readValue(definitionJson,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
            if (nodes == null || nodes.isEmpty()) {
                return null;
            }
            return nodes.stream()
                    .filter(n -> "START".equals(n.get("type")))
                    .map(n -> (String) n.get("id"))
                    .findFirst()
                    .orElse((String) nodes.get(0).get("id"));
        } catch (Exception e) {
            throw new RuntimeException("解析微流定义失败: " + e.getMessage(), e);
        }
    }

    // ===================== 数据模型 =====================

    /** 调试会话 */
    @Data
    public static class DebugSession {
        /** 会话 ID */
        private String sessionId;
        /** 微流编码 */
        private String microflowCode;
        /** 微流 ID（轨迹记录用） */
        private Long microflowId;
        /** 微流定义 JSON（会话启动时加载，调试期间不变） */
        private String definitionJson;
        /** 输入参数 */
        private Map<String, Object> inputs;
        /** 断点节点 ID 集合（并发安全） */
        private Set<String> breakpointNodeIds;
        /** 当前变量作用域（每步执行后更新） */
        private Map<String, Object> variables;
        /** 下一个待执行节点 ID（null 表示已到末尾） */
        private String currentNodeId;
        /** 当前返回结果（遇到 RETURN 节点时设置） */
        private Object result;
        /** 是否已终止（结束/失败/手动终止） */
        private boolean terminated;
        /** 最后活动时间戳（用于超时清理） */
        private long lastActivityTime;
    }

    /** 单步执行结果 */
    @Data
    public static class DebugStepResult {
        /** 本次执行的节点 ID */
        private String nodeId;
        /** 节点类型 */
        private String nodeType;
        /** 状态：PAUSED（暂停）/ COMPLETED（完成）/ FAILED（失败） */
        private String status;
        /** 执行后变量快照 */
        private Map<String, Object> variables;
        /** 执行结果（RETURN 节点的返回值） */
        private Object result;
        /** 下一节点 ID（null 表示微流已到末尾） */
        private String nextNodeId;
        /** 失败时的错误消息 */
        private String errorMessage;
    }
}
