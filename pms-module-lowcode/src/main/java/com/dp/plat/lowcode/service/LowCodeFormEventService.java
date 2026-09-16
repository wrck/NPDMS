package com.dp.plat.lowcode.service;

import java.util.Map;

/**
 * 低代码表单事件服务。
 *
 * <p>根据表单配置的 events 字段，触发对应的微流或规则。</p>
 */
public interface LowCodeFormEventService {

    /**
     * 触发表单事件。
     *
     * @param formId    表单 ID
     * @param eventType 事件类型（onLoad / onChange / onSubmit）
     * @param data      事件数据
     * @return 执行结果
     */
    Map<String, Object> triggerEvent(Long formId, String eventType, Map<String, Object> data);
}
