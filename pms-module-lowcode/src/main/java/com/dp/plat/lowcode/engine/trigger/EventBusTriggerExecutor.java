package com.dp.plat.lowcode.engine.trigger;

import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 事件总线触发器执行器：发布 Spring ApplicationEvent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBusTriggerExecutor implements TriggerExecutor {

    private final LowCodeMicroflowService microflowService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String supportedType() {
        return "EVENT";
    }

    @Override
    public Map<String, Object> execute(LowCodeTrigger trigger, Map<String, Object> data) {
        log.info("EventBus 触发器执行: trigger={}, target={}", trigger.getCode(), trigger.getTargetCode());
        // 发布 Spring 事件
        eventPublisher.publishEvent(new LowCodeTriggerEvent(trigger, data));
        // 同时直接执行目标微流
        if ("MICROFLOW".equals(trigger.getTargetType())) {
            return microflowService.execute(trigger.getTargetCode(), data);
        }
        return Map.of("message", "EventBus trigger fired, target=" + trigger.getTargetCode());
    }

    /** 自定义 Spring 事件 */
    public static class LowCodeTriggerEvent extends ApplicationEvent {
        private final LowCodeTrigger trigger;
        private final Map<String, Object> data;

        public LowCodeTriggerEvent(LowCodeTrigger trigger, Map<String, Object> data) {
            super(trigger);
            this.trigger = trigger;
            this.data = data;
        }

        public LowCodeTrigger getTrigger() {
            return trigger;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
