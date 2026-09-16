package com.dp.plat.lowcode.service.impl;

import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.service.LowCodeFormEventService;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 低代码表单事件服务实现。
 *
 * <p>解析表单 events JSON，根据事件类型（onLoad/onChange/onSubmit）配置的
 * type（MICROFLOW/RULE）和 code，分发到对应引擎执行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeFormEventServiceImpl implements LowCodeFormEventService {

    private final LowCodeFormService formService;
    private final LowCodeMicroflowService microflowService;
    private final LowCodeRuleService ruleService;
    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerEvent(Long formId, String eventType, Map<String, Object> data) {
        LowCodeForm form = formService.getById(formId);
        if (form == null || form.getEvents() == null) return Map.of();
        try {
            Map<String, Object> events = objectMapper.readValue(form.getEvents(), Map.class);
            Map<String, Object> eventConfig = (Map<String, Object>) events.get(eventType);
            if (eventConfig == null) return Map.of();
            String type = (String) eventConfig.get("type");
            String code = (String) eventConfig.get("code");
            if (type == null || code == null) return Map.of();
            return switch (type) {
                case "MICROFLOW" -> microflowService.execute(code, data);
                case "RULE" -> ruleService.execute(code, data);
                default -> Map.of();
            };
        } catch (Exception e) {
            log.error("触发表单事件失败: formId={}, eventType={}", formId, eventType, e);
            throw new RuntimeException("表单事件触发失败", e);
        }
    }
}
