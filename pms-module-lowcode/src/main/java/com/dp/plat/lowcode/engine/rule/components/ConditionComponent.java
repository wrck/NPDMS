package com.dp.plat.lowcode.engine.rule.components;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.slot.DefaultContext;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 预置条件判断组件。
 *
 * <p>从上下文读取 {@code condition} 键，若为 true 则将 {@code result} 设为 "yes"，
 * 否则设为 "no"。适用于 IF/SWITCH 分支演示。</p>
 */
@Slf4j
@LiteflowComponent("ConditionComponent")
public class ConditionComponent extends NodeComponent {

    @Override
    public void process() {
        DefaultContext context = getContextBean(DefaultContext.class);
        Object condition = context.getData("condition");
        boolean matched = Boolean.TRUE.equals(condition) || "true".equals(String.valueOf(condition));
        log.info("[LiteFlow] ConditionComponent: condition={}, matched={}", condition, matched);
        context.setData("result", matched ? "yes" : "no");
    }
}
