package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;

import java.util.Objects;

/**
 * 割接任务状态机规则（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>草稿/准备中 → 待评审（submitForReview）</li>
 *   <li>待评审 → 待执行（approve）</li>
 *   <li>待评审 → 准备中（reject）</li>
 *   <li>待执行 → 执行中（startExecution）</li>
 *   <li>执行中 → 稳定观察（completeExecution）</li>
 *   <li>稳定观察 → 稳定观察（startObservation，维持观察态）</li>
 *   <li>稳定观察 → 已完成（completeObservation）</li>
 *   <li>执行中 → 已回退（rollback）</li>
 *   <li>任意非终态 → 已终止（terminate）</li>
 * </ul>
 * 终态：已完成、已回退、已终止。
 */
public final class CutTaskStatusRules {

    public enum Action {
        SUBMIT_FOR_REVIEW,
        APPROVE,
        REJECT,
        START_EXECUTION,
        COMPLETE_EXECUTION,
        START_OBSERVATION,
        COMPLETE_OBSERVATION,
        ROLLBACK,
        TERMINATE
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
            case START_EXECUTION:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_PENDING_EXECUTION, "startExecution");
                break;
            case COMPLETE_EXECUTION:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_EXECUTING, "completeExecution");
                break;
            case START_OBSERVATION:
            case COMPLETE_OBSERVATION:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_OBSERVING, action.name());
                break;
            case ROLLBACK:
                requireCurrentIn(current, CutStatusEnum.CUT_TASK_EXECUTING, "rollback");
                break;
            case TERMINATE:
                requireNotTerminal(current);
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
                return CutStatusEnum.CUT_TASK_PENDING_EXECUTION;
            case REJECT:
                return CutStatusEnum.CUT_TASK_PREPARING;
            case START_EXECUTION:
                return CutStatusEnum.CUT_TASK_EXECUTING;
            case COMPLETE_EXECUTION:
            case START_OBSERVATION:
                return CutStatusEnum.CUT_TASK_OBSERVING;
            case COMPLETE_OBSERVATION:
                return CutStatusEnum.CUT_TASK_COMPLETED;
            case ROLLBACK:
                return CutStatusEnum.CUT_TASK_ROLLBACK;
            case TERMINATE:
                return CutStatusEnum.CUT_TASK_TERMINATED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_TASK_COMPLETED, status)
                || Objects.equals(CutStatusEnum.CUT_TASK_ROLLBACK, status)
                || Objects.equals(CutStatusEnum.CUT_TASK_TERMINATED, status);
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

    private static void requireNotTerminal(Integer current) {
        if (isTerminal(current)) {
            throw new IllegalStateException("割接任务已处于终态，无法执行任何状态变更操作");
        }
    }
}
