package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.engine.apm.LowCodeApmService;
import com.dp.plat.lowcode.engine.trigger.LowCodeTrigger;
import com.dp.plat.lowcode.engine.trigger.QuartzTriggerExecutor;
import com.dp.plat.lowcode.engine.trigger.TriggerExecutor;
import com.dp.plat.lowcode.entity.LowCodeTriggerExecutionLog;
import com.dp.plat.lowcode.mapper.LowCodeTriggerMapper;
import com.dp.plat.lowcode.service.LowCodeTriggerExecutionLogService;
import com.dp.plat.lowcode.service.LowCodeTriggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 低代码触发器服务实现。
 *
 * <p>根据触发器 type 分发到对应 TriggerExecutor 执行。
 * QUARTZ 类型触发器在保存/删除时同步注册/卸载 Quartz 调度任务（借鉴 ServiceNow Flow Designer）。</p>
 *
 * <p>每次执行都会记录执行日志（成功 / 失败均记录），用 try-finally 确保失败也记录，
 * 日志写入为 best-effort，不影响主流程。</p>
 *
 * <p><b>APM 指标</b>（批次5-T9）：触发器执行通过 {@link LowCodeApmService} 记录 Micrometer 指标，
 * APM 记录为 best-effort，服务未注入时 no-op。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeTriggerServiceImpl extends ServiceImpl<LowCodeTriggerMapper, LowCodeTrigger>
        implements LowCodeTriggerService {

    private final List<TriggerExecutor> executors;
    private final QuartzTriggerExecutor quartzTriggerExecutor;
    private final LowCodeTriggerExecutionLogService executionLogService;
    private final ObjectMapper objectMapper;

    /** APM 指标服务（可选注入，未注入时 no-op） */
    @Autowired(required = false)
    private LowCodeApmService apmService;

    @Override
    public Map<String, Object> executeTrigger(String code, Map<String, Object> data) {
        LowCodeTrigger trigger = getOne(new LambdaQueryWrapper<LowCodeTrigger>()
                .eq(LowCodeTrigger::getCode, code));
        if (trigger == null) {
            throw new RuntimeException("触发器不存在: " + code);
        }
        TriggerExecutor executor = executors.stream()
                .filter(e -> e.supportsType(trigger.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无可用执行器: " + trigger.getType()));

        // 生成执行 ID，用于串联同一次执行的微流节点轨迹
        String executionId = UUID.randomUUID().toString();
        String inputsJson = toJson(data);
        long startMs = System.currentTimeMillis();
        // result 预初始化为 null，保证 finally 中引用一定已赋值（应对 Error 等非 RuntimeException 异常路径）
        Map<String, Object> result = null;
        String status = "SUCCESS";
        String errorMessage = null;
        try {
            result = executor.execute(trigger, data);
            // 尝试从微流执行结果中提取真实的 executionId（与微流节点轨迹串联）
            String innerExecutionId = extractExecutionId(result);
            if (innerExecutionId != null) {
                executionId = innerExecutionId;
            }
        } catch (RuntimeException e) {
            result = Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            status = "FAILED";
            errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            recordExecutionLog(trigger, executionId, inputsJson, result, status, errorMessage, durationMs);
            // APM 指标记录（best-effort，apmService 可能为 null）
            if (apmService != null) {
                apmService.recordTriggerExecution(trigger.getType(), trigger.getCode(),
                        durationMs, "SUCCESS".equals(status));
            }
        }
        return result;
    }

    /** 写入执行日志（best-effort，失败仅记日志） */
    private void recordExecutionLog(LowCodeTrigger trigger, String executionId, String inputsJson,
                                    Map<String, Object> result, String status,
                                    String errorMessage, long durationMs) {
        try {
            LowCodeTriggerExecutionLog logEntry = LowCodeTriggerExecutionLog.builder()
                    .triggerId(trigger.getId())
                    .triggerCode(trigger.getCode())
                    .triggerType(trigger.getType())
                    .targetType(trigger.getTargetType())
                    .targetCode(trigger.getTargetCode())
                    .executionId(executionId)
                    .inputs(inputsJson)
                    .outputs(toJson(result))
                    .status(status)
                    .errorMessage(errorMessage)
                    .durationMs(durationMs)
                    .operator(SecurityUtils.getCurrentUsername())
                    .build();
            executionLogService.record(logEntry);
        } catch (Exception e) {
            log.error("记录触发器执行日志失败（不影响主流程）: triggerCode={}", trigger.getCode(), e);
        }
    }

    /** 安全序列化为 JSON，失败时返回 null */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("序列化触发器执行数据失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从微流执行结果中提取 executionId（如有），用于串联节点轨迹 */
    @SuppressWarnings("unchecked")
    private String extractExecutionId(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        // 微流执行结果结构：{result:..., variables:..., executionId:...}（如返回）
        Object id = result.get("executionId");
        if (id instanceof String s && !s.isBlank()) {
            return s;
        }
        // 兼容：变量中可能含 executionId
        Object variables = result.get("variables");
        if (variables instanceof Map<?, ?> v) {
            Object inner = ((Map<String, Object>) v).get("executionId");
            if (inner instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean saveOrUpdate(LowCodeTrigger trigger) {
        boolean saved = super.saveOrUpdate(trigger);
        if (saved && "QUARTZ".equals(trigger.getType())) {
            try {
                if ("ACTIVE".equals(trigger.getStatus())) {
                    quartzTriggerExecutor.scheduleJob(trigger);
                } else {
                    // 非激活状态：卸载已有调度任务
                    quartzTriggerExecutor.unscheduleJob(trigger.getCode());
                }
            } catch (Exception e) {
                // 调度注册失败不阻断保存，仅记录日志
                log.error("QUARTZ 触发器调度注册失败（不影响保存）: triggerCode={}", trigger.getCode(), e);
            }
        }
        return saved;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        LowCodeTrigger trigger = id == null ? null : getById(id);
        if (trigger != null && "QUARTZ".equals(trigger.getType())) {
            try {
                quartzTriggerExecutor.unscheduleJob(trigger.getCode());
            } catch (Exception e) {
                log.error("QUARTZ 触发器调度卸载失败（不影响删除）: triggerCode={}",
                        trigger.getCode(), e);
            }
        }
        return super.removeById(id);
    }
}
