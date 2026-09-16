package com.dp.plat.lowcode.engine.rule.components;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.slot.DefaultContext;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 预置日志组件。
 *
 * <p>在规则设计器中可通过 {@code LogComponent} 引用，将消息写入上下文 result。
 * 适用于调试和简单输出场景。</p>
 */
@Slf4j
@LiteflowComponent("LogComponent")
public class LogComponent extends NodeComponent {

    @Override
    public void process() {
        DefaultContext context = getContextBean(DefaultContext.class);
        Object input = getRequestData();
        String message = input == null ? "LogComponent executed" : input.toString();
        log.info("[LiteFlow] LogComponent: {}", message);
        context.setData("result", message);
    }
}
