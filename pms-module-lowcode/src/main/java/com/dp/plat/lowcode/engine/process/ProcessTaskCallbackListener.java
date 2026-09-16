package com.dp.plat.lowcode.engine.process;

import com.dp.plat.lowcode.engine.apm.LowCodeApmService;
import com.dp.plat.lowcode.entity.LowCodeProcessBinding;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.dp.plat.lowcode.service.LowCodeProcessBindingService;
import com.dp.plat.lowcode.service.ProcessSlaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程任务回调监听器（借鉴 ServiceNow Flow Designer 任务回调）。
 *
 * <p>在 Flowable 任务事件（create/assignment/complete）时触发绑定的微流。
 * 通过 Flowable {@link TaskListener} 机制接入，无需修改 pms-workflow 核心。</p>
 *
 * <p><b>注册方式</b>：在 BPMN 的 UserTask 上配置任务监听器
 * {@code <flowable:taskListener event="complete" delegateExpression="${processTaskCallbackListener}"/>}，
 * 与 {@code oaTaskListener} 同机制。本 Bean 名称固定为 {@code processTaskCallbackListener}。</p>
 *
 * <p><b>回调配置</b>：从 {@link LowCodeProcessBinding#getTaskCallbacks()} 读取 JSON：
 * {@code {nodeId: {onCreate: microflowCode, onAssign: microflowCode, onComplete: microflowCode}}}。
 * Flowable 事件名到回调键的映射：create→onCreate、assignment→onAssign、complete→onComplete。</p>
 *
 * <p><b>异常策略</b>：回调微流失败仅记 ERROR 日志，不向 Flowable 引擎上抛，
 * 不阻断流程主事务（与 OaTaskListener 的 best-effort 策略一致）。</p>
 *
 * <p><b>APM 指标</b>（批次5-T9）：Flowable 节点回调通过 {@link LowCodeApmService} 记录
 * Micrometer 指标，APM 记录为 best-effort，服务未注入时 no-op。</p>
 */
@Slf4j
@Component("processTaskCallbackListener")
@RequiredArgsConstructor
public class ProcessTaskCallbackListener implements TaskListener {

    private final LowCodeMicroflowService microflowService;
    private final LowCodeProcessBindingService bindingService;
    private final ObjectMapper objectMapper;

    /** APM 指标服务（可选注入，未注入时 no-op） */
    @Autowired(required = false)
    private LowCodeApmService apmService;

    /** 流程 SLA 服务（可选注入，任务完成时调用 completeSla 置 COMPLETED） */
    @Autowired(required = false)
    private ProcessSlaService processSlaService;

    /** Flowable 任务事件名 → taskCallbacks JSON 中的回调键 */
    private static final Map<String, String> EVENT_TO_CALLBACK_KEY;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("create", "onCreate");
        m.put("assignment", "onAssign");
        m.put("complete", "onComplete");
        EVENT_TO_CALLBACK_KEY = Collections.unmodifiableMap(m);
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String processDefinitionKey = null;
        String event = null;
        try {
            String processDefinitionId = delegateTask.getProcessDefinitionId();
            if (processDefinitionId == null) {
                return;
            }
            // processDefinitionId 格式为 "key:version:deploymentId"，取 key
            processDefinitionKey = processDefinitionId.split(":")[0];
            String nodeId = delegateTask.getTaskDefinitionKey();
            event = delegateTask.getEventName();
            String callbackKey = EVENT_TO_CALLBACK_KEY.get(event);
            if (callbackKey == null) {
                // 非关注事件（如 delete）→ 跳过
                return;
            }

            // 任务完成时（complete 事件）调用 SLA 服务置 COMPLETED（缺口4，best-effort）
            if ("complete".equals(event) && processSlaService != null) {
                try {
                    processSlaService.completeSla(delegateTask.getId());
                } catch (Exception slaEx) {
                    log.debug("SLA completeSla 调用失败（best-effort）: taskId={}", delegateTask.getId(), slaEx);
                }
            }

            LowCodeProcessBinding binding = bindingService.findByProcessKey(processDefinitionKey);
            if (binding == null || binding.getTaskCallbacks() == null) {
                return;
            }

            // 解析 taskCallbacks JSON，查找 nodeId + event 对应的 microflowCode
            Map<String, Map<String, String>> callbacks = parseCallbacks(binding.getTaskCallbacks());
            Map<String, String> nodeCallbacks = callbacks.get(nodeId);
            if (nodeCallbacks == null) {
                return;
            }
            String microflowCode = nodeCallbacks.get(callbackKey);
            if (microflowCode == null || microflowCode.isBlank()) {
                return;
            }

            // 触发微流，传入流程变量 + 任务上下文
            Map<String, Object> inputs = new HashMap<>(
                    delegateTask.getVariables() == null ? Collections.emptyMap() : delegateTask.getVariables());
            inputs.put("taskId", delegateTask.getId());
            inputs.put("taskName", delegateTask.getName());
            inputs.put("assignee", delegateTask.getAssignee());
            inputs.put("processInstanceId", delegateTask.getProcessInstanceId());
            inputs.put("nodeId", nodeId);
            inputs.put("eventName", event);

            microflowService.execute(microflowCode, inputs);
            log.info("流程任务回调微流成功: process={}, node={}, event={}, microflow={}",
                    processDefinitionKey, nodeId, event, microflowCode);
            // APM 指标记录（best-effort）
            if (apmService != null) {
                apmService.recordFlowableCallback(processDefinitionKey, event, true);
            }
        } catch (Exception e) {
            // 回调微流失败仅记日志，不阻断流程
            log.error("流程任务回调微流失败: taskId={}, event={}",
                    delegateTask.getId(), delegateTask.getEventName(), e);
            // APM 指标记录（best-effort）
            if (apmService != null) {
                apmService.recordFlowableCallback(processDefinitionKey, event, false);
            }
        }
    }

    /** 解析 taskCallbacks JSON 为 {nodeId: {eventKey: microflowCode}}；解析失败返回空 Map */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> parseCallbacks(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("解析 taskCallbacks JSON 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
