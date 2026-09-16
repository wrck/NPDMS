package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.engine.microflow.MicroflowContext;
import com.dp.plat.lowcode.engine.microflow.MicroflowEngine;
import com.dp.plat.lowcode.entity.LowCodeMicroflow;
import com.dp.plat.lowcode.mapper.LowCodeMicroflowMapper;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 低代码微流服务实现。
 */
@Service
@RequiredArgsConstructor
public class LowCodeMicroflowServiceImpl extends ServiceImpl<LowCodeMicroflowMapper, LowCodeMicroflow>
        implements LowCodeMicroflowService {

    private final MicroflowEngine microflowEngine;

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> inputs) {
        LowCodeMicroflow microflow = getOne(new LambdaQueryWrapper<LowCodeMicroflow>()
                .eq(LowCodeMicroflow::getCode, code));
        if (microflow == null) {
            throw new RuntimeException("微流不存在: " + code);
        }
        MicroflowContext context = microflowEngine.execute(
                microflow.getId(), microflow.getCode(), microflow.getDefinition(), inputs);
        Map<String, Object> result = new HashMap<>();
        result.put("result", context.getResult());
        result.put("variables", context.getVariables());
        return result;
    }
}
