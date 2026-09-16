package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;

import java.util.Map;

/**
 * 低代码微流服务。
 */
public interface LowCodeMicroflowService extends IService<LowCodeMicroflow> {

    /**
     * 执行微流。
     *
     * @param code   微流编码
     * @param inputs 输入参数
     * @return 执行结果（含 result 和 variables）
     */
    Map<String, Object> execute(String code, Map<String, Object> inputs);
}
