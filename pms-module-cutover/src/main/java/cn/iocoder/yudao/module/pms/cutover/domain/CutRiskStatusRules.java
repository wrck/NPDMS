package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;

/**
 * 割接风险/调研状态机规则（FR-CUT-004 / FR-CUT-006）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>待处理 → 处理中（startProcess）</li>
 *   <li>处理中 → 已闭环（close）</li>
 *   <li>待处理 → 已挂起（suspend）</li>
 * </ul>
 * 终态：已闭环。
 */
public final class CutRiskStatusRules {

    public enum Action {
        START_PROCESS,
        CLOSE,
        SUSPEND
    }

    private CutRiskStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("风险/调研项状态与动作均不能为空");
        }
        switch (action) {
            case START_PROCESS:
                requireCurrentIn(current, CutStatusEnum.CUT_RISK_OPEN, "startProcess");
                break;
            case CLOSE:
                requireCurrentIn(current, CutStatusEnum.CUT_RISK_PROCESSING, "close");
                break;
            case SUSPEND:
                requireCurrentIn(current, CutStatusEnum.CUT_RISK_OPEN, "suspend");
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case START_PROCESS:
                return CutStatusEnum.CUT_RISK_PROCESSING;
            case CLOSE:
                return CutStatusEnum.CUT_RISK_CLOSED;
            case SUSPEND:
                return CutStatusEnum.CUT_RISK_SUSPENDED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isClosed(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
