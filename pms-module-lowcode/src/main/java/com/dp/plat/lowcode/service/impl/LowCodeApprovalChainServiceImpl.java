package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeApprovalChain;
import com.dp.plat.lowcode.mapper.LowCodeApprovalChainMapper;
import com.dp.plat.lowcode.service.LowCodeApprovalChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 低代码发布多级审批链服务实现。
 */
@Slf4j
@Service
public class LowCodeApprovalChainServiceImpl
        extends ServiceImpl<LowCodeApprovalChainMapper, LowCodeApprovalChain>
        implements LowCodeApprovalChainService {

    @Override
    public List<LowCodeApprovalChain> listByConfigType(String configType) {
        return list(new LambdaQueryWrapper<LowCodeApprovalChain>()
                .eq(LowCodeApprovalChain::getConfigType, configType)
                .orderByAsc(LowCodeApprovalChain::getId));
    }

    @Override
    public LowCodeApprovalChain getEnabledByConfigType(String configType) {
        return getOne(new LambdaQueryWrapper<LowCodeApprovalChain>()
                .eq(LowCodeApprovalChain::getConfigType, configType)
                .eq(LowCodeApprovalChain::getEnabled, 1)
                .last("LIMIT 1"));
    }
}
