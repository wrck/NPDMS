package cn.iocoder.yudao.module.pms.workflow.listener;

import cn.iocoder.yudao.module.pms.platform.api.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.platform.api.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flowable 用户任务与 OA 待办同步监听器。
 *
 * <p>create 事件发布待办，complete 事件关闭待办。跨模块调用仅依赖 platform-api
 * 提供的 {@link OaTodoPort}，具体 OA 适配由 integration 模块实现，workflow 不依赖
 * integration 实现模块。</p>
 *
 * <p>OA 同步使用独立事务并采用 best-effort 策略：任何 OA 异常均在本监听器中记录后
 * 吞掉，不影响 Flowable 工作流主流程。</p>
 */
@Slf4j
@Component("oaTaskListener")
@RequiredArgsConstructor
public class OaTaskListener implements TaskListener {

    private final OaTodoPort oaTodoPort;

    /**
     * 在独立事务中同步 OA 待办，避免外部系统故障影响工作流事务。
     *
     * @param delegateTask Flowable 委托任务实例
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(DelegateTask delegateTask) {
        String eventName = delegateTask.getEventName();
        try {
            if ("create".equals(eventName)) {
                OaTodoCommand command = OaTodoCommand.builder()
                        .title(delegateTask.getName())
                        .content("待办任务：" + delegateTask.getName())
                        .handlerUserId(delegateTask.getAssignee())
                        .processInstanceId(delegateTask.getProcessInstanceId())
                        .businessKey(resolveBusinessKey(delegateTask))
                        .processUrl(resolveProcessUrl(delegateTask))
                        .businessType(delegateTask.getProcessDefinitionId())
                        .build();
                oaTodoPort.pushTodo(command);
            } else if ("complete".equals(eventName)) {
                oaTodoPort.completeTodo(delegateTask.getId());
            }
        } catch (Exception e) {
            log.warn("OA task listener '{}' failed for task {} (workflow unaffected): {}",
                    eventName, delegateTask.getId(), e.getMessage());
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
