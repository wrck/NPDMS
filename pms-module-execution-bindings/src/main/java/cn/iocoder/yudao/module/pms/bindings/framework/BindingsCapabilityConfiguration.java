package cn.iocoder.yudao.module.pms.bindings.framework;

import cn.iocoder.yudao.module.pms.bindings.backend.EventDrivenBackend;
import cn.iocoder.yudao.module.pms.bindings.backend.FieldConditionEvaluator;
import cn.iocoder.yudao.module.pms.bindings.backend.InlineSyncBackend;
import cn.iocoder.yudao.module.pms.bindings.backend.MemberConditionEvaluator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 执行后端能力声明：独立于后端实现类装配，避免"能力清单 → 定义服务 → 后端"循环依赖。
 * 不支持的过程语义显式声明为不支持，发布前校验据此拒绝。
 */
@Configuration
public class BindingsCapabilityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "pms.bindings.backends.inline-sync.enabled",
            havingValue = "true", matchIfMissing = true)
    public ExecutionBackendCapability inlineSyncBackendCapability() {
        return new ExecutionBackendCapability(InlineSyncBackend.BACKEND_ID,
                List.of(FieldConditionEvaluator.SEMANTIC_FIELD_CONDITION,
                        MemberConditionEvaluator.SEMANTIC_MEMBER_CONDITION), List.of(), false, false);
    }

    @Bean
    @ConditionalOnProperty(name = "pms.bindings.backends.event-driven.enabled",
            havingValue = "true", matchIfMissing = true)
    public ExecutionBackendCapability eventDrivenBackendCapability() {
        // 支持恢复：检查点之后的重复事件按结果形成依据幂等，不重复形成结果。
        return new ExecutionBackendCapability(EventDrivenBackend.BACKEND_ID,
                List.of(FieldConditionEvaluator.SEMANTIC_FIELD_CONDITION,
                        MemberConditionEvaluator.SEMANTIC_MEMBER_CONDITION), List.of(), true, false);
    }
}
