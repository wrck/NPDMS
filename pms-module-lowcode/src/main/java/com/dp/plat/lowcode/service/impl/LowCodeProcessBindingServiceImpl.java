package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeProcessBinding;
import com.dp.plat.lowcode.mapper.LowCodeProcessBindingMapper;
import com.dp.plat.lowcode.service.LowCodeProcessBindingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 低代码流程绑定服务实现。
 */
@Service
@RequiredArgsConstructor
public class LowCodeProcessBindingServiceImpl extends ServiceImpl<LowCodeProcessBindingMapper, LowCodeProcessBinding>
        implements LowCodeProcessBindingService {

    private final ObjectMapper objectMapper;

    @Override
    public LowCodeProcessBinding findByProcessKey(String processDefinitionKey) {
        return getOne(new LambdaQueryWrapper<LowCodeProcessBinding>()
                .eq(LowCodeProcessBinding::getProcessDefinitionKey, processDefinitionKey));
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getFormCodeForNode(String processDefinitionKey, String nodeId) {
        LowCodeProcessBinding binding = findByProcessKey(processDefinitionKey);
        if (binding == null) return null;
        try {
            List<Map<String, Object>> bindings = objectMapper.readValue(
                    binding.getNodeFormBindings(),
                    new TypeReference<List<Map<String, Object>>>() {});
            return bindings.stream()
                    .filter(b -> nodeId.equals(b.get("nodeId")))
                    .map(b -> (String) b.get("formCode"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
