package com.dp.plat.lowcode.engine.rule.components;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.slot.DefaultContext;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 预置赋值组件。
 *
 * <p>将上下文中的 {@code value} 键赋值到 {@code result} 键，
 * 适用于数据传递和结果返回场景。</p>
 */
@Slf4j
@LiteflowComponent("AssignComponent")
public class AssignComponent extends NodeComponent {

    @Override
    public void process() {
        DefaultContext context = getContextBean(DefaultContext.class);
        Object value = context.getData("value");
        log.info("[LiteFlow] AssignComponent: value={}", value);
        // DefaultContext.setData 不接受 null 值，null 时设置空字符串
        context.setData("result", value != null ? value : "");
    }
}
