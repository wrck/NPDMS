package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.engine.trigger.LowCodeTrigger;

import java.util.Map;

/**
 * 低代码触发器服务。
 */
public interface LowCodeTriggerService extends IService<LowCodeTrigger> {

    /**
     * 执行触发器。
     *
     * @param code 触发器编码
     * @param data 触发数据
     * @return 执行结果
     */
    Map<String, Object> executeTrigger(String code, Map<String, Object> data);
}
