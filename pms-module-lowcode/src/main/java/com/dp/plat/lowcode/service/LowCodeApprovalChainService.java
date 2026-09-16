package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeApprovalChain;

import java.util.List;

/**
 * 低代码发布多级审批链服务。
 */
public interface LowCodeApprovalChainService extends IService<LowCodeApprovalChain> {

    /**
     * 按配置类型查询审批链列表。
     *
     * @param configType 配置类型
     * @return 审批链列表
     */
    List<LowCodeApprovalChain> listByConfigType(String configType);

    /**
     * 查询指定配置类型当前启用的审批链（仅一条，无则返回 null）。
     *
     * @param configType 配置类型
     * @return 启用的审批链，无则 null
     */
    LowCodeApprovalChain getEnabledByConfigType(String configType);
}
