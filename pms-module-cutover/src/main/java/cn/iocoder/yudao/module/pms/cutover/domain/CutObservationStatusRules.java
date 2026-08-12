package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;

/**
 * 稳定观察状态机规则（FR-CUT-013 / FR-CUT-014）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>观察中 → 已通过（pass）</li>
 *   <li>观察中 → 异常（markAbnormal）</li>
 *   <li>已通过 → 已归档（archive）</li>
 * </ul>
 * 终态：已归档。
 */
public final class CutObservationStatusRules {

    public enum Action {
        PASS,
        MARK_ABNORMAL,
        ARCHIVE
    }

    private CutObservationStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("观察记录状态与动作均不能为空");
        }
        switch (action) {
            case PASS:
            case MARK_ABNORMAL:
                requireCurrentIn(current, CutStatusEnum.CUT_OBSERVATION_OBSERVING, action.name());
                break;
            case ARCHIVE:
                requireCurrentIn(current, CutStatusEnum.CUT_OBSERVATION_PASSED, "archive");
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case PASS:
                return CutStatusEnum.CUT_OBSERVATION_PASSED;
            case MARK_ABNORMAL:
                return CutStatusEnum.CUT_OBSERVATION_ABNORMAL;
            case ARCHIVE:
                return CutStatusEnum.CUT_OBSERVATION_ARCHIVED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_OBSERVATION_ARCHIVED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
