package com.dp.plat.lowcode.engine.trigger;

import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * CRUD 触发器执行器：监听动态实体 CRUD 事件。
 *
 * <p>CRUD 触发器 config JSON 约定：
 * <pre>
 * {
 *   "entityCode": "order",
 *   "operations": ["CREATE", "UPDATE", "DELETE"],
 *   "timing": ["BEFORE", "AFTER"]
 * }
 * </pre>
 * 由 {@link com.dp.plat.lowcode.engine.DynamicEntityDataService} 在 create/update/delete
 * 前后查询匹配的触发器并调用本执行器执行目标微流。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudTriggerExecutor implements TriggerExecutor {

    private final LowCodeMicroflowService microflowService;
    private final ObjectMapper objectMapper;

    @Override
    public String supportedType() {
        return "CRUD";
    }

    @Override
    public Map<String, Object> execute(LowCodeTrigger trigger, Map<String, Object> data) {
        log.info("CRUD 触发器执行: trigger={}, target={}", trigger.getCode(), trigger.getTargetCode());
        if ("MICROFLOW".equals(trigger.getTargetType())) {
            return microflowService.execute(trigger.getTargetCode(), data);
        }
        return Map.of("message", "CRUD trigger fired, target=" + trigger.getTargetCode());
    }

    /**
     * 判断触发器是否匹配指定的实体 / 操作 / 时机。
     *
     * <p>config 缺省时按宽松匹配（不限制），保证向后兼容旧 config 结构。</p>
     *
     * @param trigger    触发器
     * @param entityCode 实体编码
     * @param operation  操作类型：CREATE / UPDATE / DELETE
     * @param timing     时机：BEFORE / AFTER
     * @return 是否匹配
     */
    @SuppressWarnings("unchecked")
    public boolean matches(LowCodeTrigger trigger, String entityCode, String operation, String timing) {
        if (trigger.getConfig() == null || trigger.getConfig().isBlank()) {
            return true;
        }
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(trigger.getConfig(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 CRUD 触发器 config 失败，按宽松匹配: trigger={}", trigger.getCode(), e);
            return true;
        }

        Object cfgEntityCode = config.get("entityCode");
        if (cfgEntityCode != null && !cfgEntityCode.toString().equals(entityCode)) {
            return false;
        }

        List<String> operations = asStringList(config.get("operations"));
        if (operations != null && !operations.isEmpty() && !operations.contains(operation)) {
            return false;
        }

        List<String> timings = asStringList(config.get("timing"));
        if (timings != null && !timings.isEmpty() && !timings.contains(timing)) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List) {
            return ((List<Object>) value).stream()
                    .map(String::valueOf)
                    .map(String::toUpperCase)
                    .toList();
        }
        return null;
    }
}
