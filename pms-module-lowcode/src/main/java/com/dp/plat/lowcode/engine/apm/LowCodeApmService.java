package com.dp.plat.lowcode.engine.apm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 低代码 APM 全链路指标采集服务（批次5-T9）。
 *
 * <p>借鉴 Joget APM，为低代码平台四大执行引擎提供统一的 Micrometer 指标入口：</p>
 * <ul>
 *   <li>微流执行（{@code MicroflowEngine}）：总执行次数/耗时 + 节点级耗时</li>
 *   <li>规则执行（{@code RuleEngineServiceImpl}）：决策表 / 表达式 / LiteFlow 次数与耗时</li>
 *   <li>连接器调用（{@code LowCodeConnectorServiceImpl}）：REST / DB / MQ / FILE 次数与耗时</li>
 *   <li>触发器执行（{@code LowCodeTriggerServiceImpl}）：各类型触发器次数与耗时</li>
 *   <li>Flowable 节点回调（{@code ProcessTaskCallbackListener}）：流程任务回调微流次数</li>
 * </ul>
 *
 * <p><b>指标命名</b>（Prometheus 抓取格式 {@code lowcode_*}）：</p>
 * <pre>
 * lowcode_microflow_execution_total{microflow_code, status}       Counter
 * lowcode_microflow_duration_seconds{microflow_code}               Timer
 * lowcode_microflow_node_duration_seconds{node_type, status}       Timer
 * lowcode_rule_execution_total{rule_type, status}                  Counter
 * lowcode_rule_duration_seconds{rule_type}                         Timer
 * lowcode_connector_call_total{connector_type, connector_code, status}  Counter
 * lowcode_connector_duration_seconds{connector_type, connector_code}    Timer
 * lowcode_trigger_execution_total{trigger_type, trigger_code, status}   Counter
 * lowcode_trigger_duration_seconds{trigger_type, trigger_code}          Timer
 * lowcode_flowable_callback_total{process_key, event, status}     Counter
 * </pre>
 *
 * <p><b>异常安全</b>：所有方法均为 best-effort — {@link MeterRegistry} 未注入
 * （单元测试 / 无 actuator 环境）时全部 no-op；指标记录异常仅记 WARN 日志，
 * 不影响业务主流程。各业务引擎通过字段注入 {@code @Autowired(required=false)}
 * 引用本服务，同样 null-skip，确保监控逻辑不拖累主流程。</p>
 *
 * @see com.dp.plat.common.metrics.BusinessMetrics
 * @see com.dp.plat.common.mybatis.SlowSqlInterceptor
 */
@Slf4j
@Component
public class LowCodeApmService {

    /** Micrometer 指标注册中心（可选注入，未注入时所有方法 no-op）。 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造器注入 {@link MeterRegistry}。
     *
     * <p>使用 {@code required = false} 使本 Bean 在无 actuator / 测试环境下也能实例化，
     * 此时所有 {@code record*} 方法因 {@code meterRegistry == null} 直接返回（no-op）。
     * 运行时（pms-admin）由 {@code spring-boot-starter-actuator} +
     * {@code micrometer-registry-prometheus} 提供 PrometheusMeterRegistry。</p>
     *
     * @param meterRegistry Micrometer 指标注册中心（可为 null）
     */
    @Autowired
    public LowCodeApmService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // =========================================================================
    // 微流执行指标
    // =========================================================================

    /**
     * 记录微流整体执行（含成功/失败状态与耗时）。
     *
     * @param microflowCode 微流编码（为 null 时记为 "unknown"）
     * @param durationMs    执行耗时（毫秒）
     * @param success       是否执行成功
     */
    public void recordMicroflowExecution(String microflowCode, long durationMs, boolean success) {
        if (meterRegistry == null) return;
        try {
            String code = safeTag(microflowCode);
            String status = success ? "SUCCESS" : "FAILED";
            Counter.builder("lowcode_microflow_execution_total")
                    .description("低代码微流执行总次数")
                    .tag("microflow_code", code)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("lowcode_microflow_duration_seconds")
                    .description("低代码微流执行耗时")
                    .tag("microflow_code", code)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[APM] 记录微流执行指标失败: {}", e.getMessage());
        }
    }

    /**
     * 记录微流单节点执行耗时。
     *
     * @param nodeType  节点类型（ASSIGN / CONDITION / RETURN / CALL_CONNECTOR 等）
     * @param durationMs 节点执行耗时（毫秒）
     * @param success   节点是否执行成功
     */
    public void recordMicroflowNodeExecution(String nodeType, long durationMs, boolean success) {
        if (meterRegistry == null) return;
        try {
            String type = safeTag(nodeType);
            String status = success ? "SUCCESS" : "FAILED";
            Timer.builder("lowcode_microflow_node_duration_seconds")
                    .description("低代码微流节点执行耗时")
                    .tag("node_type", type)
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[APM] 记录微流节点指标失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 规则执行指标
    // =========================================================================

    /**
     * 记录规则执行（决策表 / 表达式 / LiteFlow）。
     *
     * @param ruleType   规则类型：{@code decision-table} / {@code expression} / {@code liteflow}
     * @param durationMs 执行耗时（毫秒）
     * @param success    是否执行成功
     */
    public void recordRuleExecution(String ruleType, long durationMs, boolean success) {
        if (meterRegistry == null) return;
        try {
            String type = safeTag(ruleType);
            String status = success ? "SUCCESS" : "FAILED";
            Counter.builder("lowcode_rule_execution_total")
                    .description("低代码规则执行总次数")
                    .tag("rule_type", type)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("lowcode_rule_duration_seconds")
                    .description("低代码规则执行耗时")
                    .tag("rule_type", type)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[APM] 记录规则执行指标失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 连接器调用指标
    // =========================================================================

    /**
     * 记录连接器调用（REST / DB / MQ / FILE）。
     *
     * @param connectorType 连接器类型（REST / DB / MQ / FILE）
     * @param connectorCode 连接器编码（为 null 时记为 "unknown"）
     * @param durationMs    调用耗时（毫秒）
     * @param success       是否调用成功
     */
    public void recordConnectorCall(String connectorType, String connectorCode,
                                    long durationMs, boolean success) {
        if (meterRegistry == null) return;
        try {
            String type = safeTag(connectorType);
            String code = safeTag(connectorCode);
            String status = success ? "SUCCESS" : "FAILED";
            Counter.builder("lowcode_connector_call_total")
                    .description("低代码连接器调用总次数")
                    .tag("connector_type", type)
                    .tag("connector_code", code)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("lowcode_connector_duration_seconds")
                    .description("低代码连接器调用耗时")
                    .tag("connector_type", type)
                    .tag("connector_code", code)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[APM] 记录连接器调用指标失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 触发器执行指标
    // =========================================================================

    /**
     * 记录触发器执行。
     *
     * @param triggerType  触发器类型（CRUD / QUARTZ / EVENT_BUS 等）
     * @param triggerCode  触发器编码（为 null 时记为 "unknown"）
     * @param durationMs   执行耗时（毫秒）
     * @param success      是否执行成功
     */
    public void recordTriggerExecution(String triggerType, String triggerCode,
                                       long durationMs, boolean success) {
        if (meterRegistry == null) return;
        try {
            String type = safeTag(triggerType);
            String code = safeTag(triggerCode);
            String status = success ? "SUCCESS" : "FAILED";
            Counter.builder("lowcode_trigger_execution_total")
                    .description("低代码触发器执行总次数")
                    .tag("trigger_type", type)
                    .tag("trigger_code", code)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("lowcode_trigger_duration_seconds")
                    .description("低代码触发器执行耗时")
                    .tag("trigger_type", type)
                    .tag("trigger_code", code)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[APM] 记录触发器执行指标失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Flowable 流程回调指标
    // =========================================================================

    /**
     * 记录 Flowable 任务回调微流执行。
     *
     * @param processKey 流程定义 key
     * @param event      事件名（create / assignment / complete）
     * @param success    回调微流是否执行成功
     */
    public void recordFlowableCallback(String processKey, String event, boolean success) {
        if (meterRegistry == null) return;
        try {
            String key = safeTag(processKey);
            String evt = safeTag(event);
            String status = success ? "SUCCESS" : "FAILED";
            Counter.builder("lowcode_flowable_callback_total")
                    .description("低代码 Flowable 任务回调次数")
                    .tag("process_key", key)
                    .tag("event", evt)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("[APM] 记录 Flowable 回调指标失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /**
     * 安全化 tag 值：null/空 → "unknown"，超长截断（Micrometer tag 值不宜过长，影响 cardinality）。
     */
    private String safeTag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() > 128 ? value.substring(0, 128) : value;
    }
}
