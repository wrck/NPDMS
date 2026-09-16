package com.dp.plat.lowcode.engine.rule;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import com.yomahub.liteflow.slot.DefaultContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LiteFlow 规则执行器（借鉴 pms-rules 规则集编排能力）。
 *
 * <p>封装 LiteFlow 2.15.0 的 {@link FlowExecutor#execute2RespWithEL} 调用，
 * 将上下文变量写入 {@link DefaultContext}，执行 EL 表达式后从上下文读取 result。
 * 若组件未写入 result，则返回 null。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteFlowExecutor {

    private final FlowExecutor flowExecutor;

    /**
     * 执行 LiteFlow EL。
     *
     * @param el      LiteFlow 表达式，如 "THEN(a,b,c)" 或 "SWITCH(x).to(a,b,c)"
     * @param context 上下文变量，写入 DefaultContext 供组件读取
     * @return 执行结果（取自上下文 result 键），无结果时返回 null
     */
    public Object execute(String el, Map<String, Object> context) {
        if (el == null || el.isBlank()) {
            return null;
        }
        try {
            DefaultContext liteflowContext = new DefaultContext();
            if (context != null) {
                for (Map.Entry<String, Object> e : context.entrySet()) {
                    liteflowContext.setData(e.getKey(), e.getValue());
                }
            }
            // LiteFlow 2.15.0：execute2RespWithEL(el, param, requestId, contextBeans...)
            LiteflowResponse response = flowExecutor.execute2RespWithEL(
                    el, null, null, liteflowContext);
            if (response == null) {
                throw new RuntimeException("LiteFlow 返回空响应");
            }
            if (!response.isSuccess()) {
                Throwable cause = response.getCause();
                String errorMsg = cause == null ? "unknown" : cause.getMessage();
                throw new RuntimeException(
                        "LiteFlow 执行失败: " + el + " — " + errorMsg, cause);
            }
            // 从执行后的上下文读取 result（组件可 setData("result", value)）
            DefaultContext resultContext = response.getFirstContextBean();
            return resultContext == null ? null : resultContext.getData("result");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LiteFlow 执行失败: " + el + " — " + e.getMessage(), e);
        }
    }
}
