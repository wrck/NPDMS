package cn.iocoder.yudao.module.pms.commerce.domain.scope;

import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.STATE_CONFLICT;

/** append-only范围历史的最小状态守卫。 */
public final class DeliveryScopeStateMachine {

    private static final Set<String> STATES = Set.of("ACTIVE", "RELEASED", "CONFLICT");

    public void requireAdjustable(String current) {
        requireCurrent(current, "ACTIVE");
    }

    public void requireReleasable(String current) {
        requireCurrent(current, "ACTIVE");
    }

    public void requireResolvable(String current) {
        requireCurrent(current, "CONFLICT");
    }

    public void requireKnown(String state) {
        if (!STATES.contains(state)) {
            throw new CommerceDeliveryScopeCommandException(STATE_CONFLICT, "未知交付范围状态");
        }
    }

    private void requireCurrent(String actual, String expected) {
        requireKnown(actual);
        if (!expected.equals(actual)) {
            throw new CommerceDeliveryScopeCommandException(STATE_CONFLICT,
                    "交付范围状态不允许当前操作: " + actual);
        }
    }
}
