package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceViews;

import java.util.Objects;

/**
 * 到货签收 HTTP 受信上下文边界。
 *
 * <p>Task 8 只形成 Controller 契约，不注册生产实现；Task 12 在 COM/AST 正式依赖接通时提供唯一实现。</p>
 */
public interface ArrivalAcceptanceRequestContext {

    TrustedContext current();

    record TrustedContext(Long tenantId, Long actorUserId, String correlationId,
                          ArrivalAcceptanceViews.AccessContext access) {
        public TrustedContext {
            if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0
                    || correlationId == null || correlationId.isBlank()
                    || !correlationId.equals(correlationId.trim()) || correlationId.length() > 128
                    || access == null || !Objects.equals(actorUserId, access.actorUserId())) {
                throw new IllegalArgumentException("invalid trusted arrival request context");
            }
        }
    }
}
