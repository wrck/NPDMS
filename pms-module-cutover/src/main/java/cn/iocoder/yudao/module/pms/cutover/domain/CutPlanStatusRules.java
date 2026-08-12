package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;

/**
 * 割接方案状态机规则（FR-CUT-008 / FR-CUT-009）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>草稿 → 待评审（submit）</li>
 *   <li>待评审 → 已通过（approve，冻结基线版本号）</li>
 *   <li>待评审 → 已驳回（reject）</li>
 *   <li>待评审 → 已终止（terminate）</li>
 * </ul>
 * 终态：已通过、已驳回、已终止。已通过状态形成不可覆盖的基线版本。
 */
public final class CutPlanStatusRules {

    public enum Action {
        SUBMIT,
        APPROVE,
        REJECT,
        TERMINATE
    }

    private CutPlanStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("割接方案状态与动作均不能为空");
        }
        switch (action) {
            case SUBMIT:
                requireCurrentIn(current, CutStatusEnum.CUT_PLAN_DRAFT, "submit");
                break;
            case APPROVE:
            case REJECT:
            case TERMINATE:
                requireCurrentIn(current, CutStatusEnum.CUT_PLAN_PENDING_REVIEW, action.name());
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case SUBMIT:
                return CutStatusEnum.CUT_PLAN_PENDING_REVIEW;
            case APPROVE:
                return CutStatusEnum.CUT_PLAN_APPROVED;
            case REJECT:
                return CutStatusEnum.CUT_PLAN_REJECTED;
            case TERMINATE:
                return CutStatusEnum.CUT_PLAN_TERMINATED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isBaselineLocked(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_PLAN_APPROVED, status);
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_PLAN_APPROVED, status)
                || Objects.equals(CutStatusEnum.CUT_PLAN_REJECTED, status)
                || Objects.equals(CutStatusEnum.CUT_PLAN_TERMINATED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
