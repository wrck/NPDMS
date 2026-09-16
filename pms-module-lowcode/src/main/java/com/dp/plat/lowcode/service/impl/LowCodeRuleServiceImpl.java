package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.engine.rule.RuleEngineService;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.entity.LowCodeRule;
import com.dp.plat.lowcode.mapper.LowCodeRuleMapper;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.service.LowCodeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 低代码规则服务实现。
 *
 * <p>根据规则类型分发到对应执行方法：决策表 / 表达式 / LiteFlow。
 * 发布时复用 {@link LowCodeConfigVersionService} 生成不可变版本快照，支持版本回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class LowCodeRuleServiceImpl extends ServiceImpl<LowCodeRuleMapper, LowCodeRule>
        implements LowCodeRuleService {

    /** 规则在版本管理中的配置类型标识 */
    private static final String CONFIG_TYPE_RULE = "RULE";

    private final RuleEngineService ruleEngineService;
    private final LowCodeConfigVersionService configVersionService;

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> facts) {
        LowCodeRule rule = getOne(new LambdaQueryWrapper<LowCodeRule>()
                .eq(LowCodeRule::getCode, code));
        if (rule == null) {
            throw new RuntimeException("规则不存在: " + code);
        }
        Map<String, Object> result = new HashMap<>();
        switch (rule.getType()) {
            case "DECISION_TABLE" -> result.put("actions", ruleEngineService.executeDecisionTable(rule.getDefinition(), facts));
            case "EXPRESSION" -> result.put("result", ruleEngineService.executeExpression(rule.getDefinition(), facts));
            case "LITEFLOW" -> result.put("result", ruleEngineService.executeLiteFlow(rule.getDefinition(), facts));
            default -> throw new IllegalArgumentException("未知规则类型: " + rule.getType());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeConfigVersion publishWithVersion(Long ruleId) {
        LowCodeRule rule = getById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在: " + ruleId);
        }
        rule.setStatus("PUBLISHED");
        updateById(rule);
        // 重新加载获取乐观锁自增后的版本号
        LowCodeRule published = getById(ruleId);
        return configVersionService.createSnapshot(
                new LowCodeConfigVersionService.SnapshotContext(
                        CONFIG_TYPE_RULE, published.getId(), published.getCode(),
                        published.getDefinition(),
                        "规则发布 v" + published.getVersion()
                )
        );
    }

    @Override
    public List<LowCodeConfigVersion> listRuleVersions(Long ruleId) {
        return configVersionService.getVersionHistory(CONFIG_TYPE_RULE, ruleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackRule(Long ruleId, Integer targetVersion) {
        LowCodeRule rule = getById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在: " + ruleId);
        }
        // 在版本历史中定位目标版本快照
        LowCodeConfigVersion target = configVersionService.getVersionHistory(CONFIG_TYPE_RULE, ruleId)
                .stream()
                .filter(v -> v.getVersion().equals(targetVersion))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "版本不存在: " + CONFIG_TYPE_RULE + "/" + ruleId + "/v" + targetVersion));
        // 用历史快照覆盖当前规则定义（@Version 乐观锁自动递增）
        rule.setDefinition(target.getSnapshot());
        updateById(rule);
        // 记录回滚为新版本快照（不删除历史）
        configVersionService.rollback(CONFIG_TYPE_RULE, ruleId, targetVersion,
                "回滚到版本 " + targetVersion);
    }
}
