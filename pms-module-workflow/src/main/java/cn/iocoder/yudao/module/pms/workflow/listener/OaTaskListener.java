package cn.iocoder.yudao.module.pms.workflow.listener;

import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Mirrors Flowable create/complete events to the optional OA integration port.
 * Registered by BPMN delegateExpression="${oaTaskListener}".
 *
 * <p>The integration adapter owns its independent transaction. This listener is
 * deliberately outside that transaction interceptor: both invocation failures
 * and failures while committing the adapter transaction must be caught here,
 * rather than escaping after notify() has already returned.</p>
 *
 * <p>Missing integration and failed OA calls are best-effort and do not fail the
 * workflow. A database failure may prevent an integration log from being saved;
 * catching it does not constitute evidence that the log was committed.</p>
 */
@Slf4j
@Component("oaTaskListener")
@RequiredArgsConstructor
public class OaTaskListener implements TaskListener {

    private final ObjectProvider<OaTodoPort> oaTodoPortProvider;

    @Override
    public void notify(DelegateTask delegateTask) {
        String eventName = delegateTask.getEventName();
        if (!"create".equals(eventName) && !"complete".equals(eventName)) {
            return;
        }
        try {
            OaTodoPort oaTodoPort = oaTodoPortProvider.getIfAvailable();
            if (oaTodoPort == null) {
                log.debug("OA integration is not loaded; skip task {} event {}",
                        delegateTask.getId(), eventName);
                return;
            }
            // Preserve the existing outgoing business-key convention. Completion
            // must resolve the same key, not unconditionally use a task ID.
            String businessKey = resolveBusinessKey(delegateTask);
            if ("create".equals(eventName)) {
                OaTodoCommand command = OaTodoCommand.builder()
                        .title(delegateTask.getName())
                        .content("待办任务：" + delegateTask.getName())
                        .handlerUserId(delegateTask.getAssignee())
                        .processInstanceId(delegateTask.getProcessInstanceId())
                        .businessKey(businessKey)
                        .processUrl(resolveProcessUrl(delegateTask))
                        .businessType(delegateTask.getProcessDefinitionId())
                        .build();
                oaTodoPort.pushTodo(command);
            } else {
                oaTodoPort.completeTodo(businessKey);
            }
        } catch (Exception e) {
            log.warn("OA task listener '{}' failed for task {} (workflow unaffected): {}",
                    eventName, delegateTask.getId(), e.getMessage(), e);
        }
    }

    private String resolveBusinessKey(DelegateTask delegateTask) {
        try {
            Object value = delegateTask.getVariable("businessKey");
            return value == null ? delegateTask.getId() : value.toString();
        } catch (Exception e) {
            return delegateTask.getId();
        }
    }

    private String resolveProcessUrl(DelegateTask delegateTask) {
        try {
            Object value = delegateTask.getVariable("processUrl");
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
