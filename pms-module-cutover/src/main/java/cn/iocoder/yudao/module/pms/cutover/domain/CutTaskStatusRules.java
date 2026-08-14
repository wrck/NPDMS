package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;
import java.util.Set;

/**
 * 割接任务状态机规则（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>草稿/准备中 → 待评审（submitForReview）</li>
 *   <li>待评审 → 闭环中（approve，进入P6）</li>
 *   <li>待评审 → 准备中（reject）</li>
 * </ul>
 * P6闭环提交由后续CUT-06 Feature实现；本规则不暴露逐步骤执行或稳定观察动作。
 */
public final class CutTaskStatusRules {

    public enum Action {
        SUBMIT_FOR_REVIEW,
        APPROVE,
        REJECT
    }

    private CutTaskStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("割接任务状态与动作均不能为空");
        }
        switch (action) {
            case SUBMIT_FOR_REVIEW:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_DRAFT, CutStatusEnum.CUT_TASK_PREPARING, "submitForReview");
                break;
            case APPROVE:
            case REJECT:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_PENDING_REVIEW, action.name());
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case SUBMIT_FOR_REVIEW:
                return CutStatusEnum.CUT_TASK_PENDING_REVIEW;
            case APPROVE:
                return CutStatusEnum.CUT_TASK_CLOSURE_IN_PROGRESS;
            case REJECT:
                return CutStatusEnum.CUT_TASK_PREPARING;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        // 旧实现中的6/7/8仅用于阻止历史记录被当前通用更新接口改写。
        return status != null && Set.of(6, 7, 8).contains(status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }

    private static void requireCurrentIn(Integer current, Integer expected1, Integer expected2, String actionName) {
        if (!Objects.equals(expected1, current) && !Objects.equals(expected2, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected1 + " 或 " + expected2 + "，实际为 " + current);
        }
    }

}
