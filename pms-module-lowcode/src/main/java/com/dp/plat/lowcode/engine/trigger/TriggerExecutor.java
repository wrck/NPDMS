package com.dp.plat.lowcode.engine.trigger;

import java.util.Map;

/**
 * 触发器执行器接口。
 *
 * <p>每种触发类型（CRUD/QUARTZ/EVENT）对应一个执行器实现，
 * 负责调用目标微流或流程。</p>
 */
public interface TriggerExecutor {

    /**
     * 支持的触发类型。
     */
    String supportedType();

    /**
     * 是否支持指定触发类型（默认按 supportedType 精确匹配）。
     */
    default boolean supportsType(String type) {
        return supportedType().equals(type);
    }

    /**
     * 执行触发：调用目标微流/流程。
     *
     * @param trigger 触发器配置
     * @param data    触发数据
     * @return 执行结果
     */
    Map<String, Object> execute(LowCodeTrigger trigger, Map<String, Object> data);
}
