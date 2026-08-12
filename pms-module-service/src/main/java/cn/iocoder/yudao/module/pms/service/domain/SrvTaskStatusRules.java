package cn.iocoder.yudao.module.pms.service.domain;

import cn.iocoder.yudao.module.pms.service.enums.SrvStatusEnum;

import java.util.Objects;

/**
 * 巡检任务状态机规则（FR-SRV-001 / FR-SRV-012）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>草稿 → 待执行（submit，提交计划）</li>
 *   <li>待执行 → 执行中（startExecution，开始执行）</li>
 *   <li>执行中 → 待确认（completeExecution，完成执行待确认）</li>
 *   <li>待确认 → 已完成（confirmReport，确认报告闭环）</li>
 *   <li>任意非终态 → 已取消（cancel，异常取消）</li>
 * </ul>
 * 终态：已完成、已取消。
 */
public final class SrvTaskStatusRules {

    public enum Action {
        SUBMIT,
        START_EXECUTION,
        COMPLETE_EXECUTION,
        CONFIRM_REPORT,
        CANCEL
    }

    private SrvTaskStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("巡检任务状态与动作均不能为空");
        }
        switch (action) {
            case SUBMIT:
                requireCurrentIn(current, SrvStatusEnum.SRV_TASK_DRAFT, "submit");
                break;
            case START_EXECUTION:
                requireCurrentIn(current, SrvStatusEnum.SRV_TASK_PENDING, "startExecution");
                break;
            case COMPLETE_EXECUTION:
                requireCurrentIn(current, SrvStatusEnum.SRV_TASK_EXECUTING, "completeExecution");
                break;
            case CONFIRM_REPORT:
                requireCurrentIn(current, SrvStatusEnum.SRV_TASK_PENDING_CONFIRM, "confirmReport");
                break;
            case CANCEL:
                requireNotTerminal(current);
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case SUBMIT:
                return SrvStatusEnum.SRV_TASK_PENDING;
            case START_EXECUTION:
                return SrvStatusEnum.SRV_TASK_EXECUTING;
            case COMPLETE_EXECUTION:
                return SrvStatusEnum.SRV_TASK_PENDING_CONFIRM;
            case CONFIRM_REPORT:
                return SrvStatusEnum.SRV_TASK_COMPLETED;
            case CANCEL:
                return SrvStatusEnum.SRV_TASK_CANCELLED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(SrvStatusEnum.SRV_TASK_COMPLETED, status)
                || Objects.equals(SrvStatusEnum.SRV_TASK_CANCELLED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }

    private static void requireNotTerminal(Integer current) {
        if (isTerminal(current)) {
            throw new IllegalStateException("巡检任务已处于终态，无法执行任何状态变更操作");
        }
    }
}
