package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;

/**
 * 割接执行记录状态机规则（FR-CUT-011 / FR-CUT-012）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>待执行 → 执行中（start）</li>
 *   <li>执行中 → 已通过（pass）</li>
 *   <li>执行中 → 失败（fail）</li>
 *   <li>执行中 → 已回退（rollback）</li>
 * </ul>
 * 终态：已通过、失败、已回退。
 */
public final class CutExecutionStatusRules {

    public enum Action {
        START,
        PASS,
        FAIL,
        ROLLBACK
    }

    private CutExecutionStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("执行记录状态与动作均不能为空");
        }
        switch (action) {
            case START:
                requireCurrentIn(current, CutStatusEnum.CUT_EXECUTION_PENDING, "start");
                break;
            case PASS:
            case FAIL:
            case ROLLBACK:
                requireCurrentIn(current, CutStatusEnum.CUT_EXECUTION_EXECUTING, action.name());
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case START:
                return CutStatusEnum.CUT_EXECUTION_EXECUTING;
            case PASS:
                return CutStatusEnum.CUT_EXECUTION_PASSED;
            case FAIL:
                return CutStatusEnum.CUT_EXECUTION_FAILED;
            case ROLLBACK:
                return CutStatusEnum.CUT_EXECUTION_ROLLBACK;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_EXECUTION_PASSED, status)
                || Objects.equals(CutStatusEnum.CUT_EXECUTION_FAILED, status)
                || Objects.equals(CutStatusEnum.CUT_EXECUTION_ROLLBACK, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
