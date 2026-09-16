package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeProcessBinding;

/**
 * 低代码流程绑定服务。
 */
public interface LowCodeProcessBindingService extends IService<LowCodeProcessBinding> {

    /**
     * 根据流程定义 key 查询绑定。
     *
     * @param processDefinitionKey 流程定义 key
     * @return 流程绑定，不存在返回 null
     */
    LowCodeProcessBinding findByProcessKey(String processDefinitionKey);

    /**
     * 根据 task 节点 ID 获取绑定的表单 code。
     *
     * @param processDefinitionKey 流程定义 key
     * @param nodeId               节点 ID
     * @return 表单 code，未绑定返回 null
     */
    String getFormCodeForNode(String processDefinitionKey, String nodeId);
}
