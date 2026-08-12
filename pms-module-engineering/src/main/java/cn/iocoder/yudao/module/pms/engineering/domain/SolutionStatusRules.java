package cn.iocoder.yudao.module.pms.engineering.domain;

import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;

import java.util.Objects;

/**
 * 实施方案状态机规则（FR-ENG-016 / FR-ENG-011）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>草稿 → 已提交（submit）</li>
 *   <li>已提交 → 审批中（startReview）</li>
 *   <li>审批中 → 已通过（approve，重大方案自动升级到总部复审仍处于审批中）</li>
 *   <li>审批中 → 已驳回（reject）</li>
 *   <li>已提交/审批中 → 已撤回（withdraw）</li>
 *   <li>任意非终态 → 已终止（terminate）</li>
 * </ul>
 * 终态：已通过、已驳回、已撤回、已终止。已通过状态形成不可覆盖的基线版本。
 */
public final class SolutionStatusRules {

    public enum Action {
        SUBMIT,
        START_REVIEW,
        APPROVE,
        REJECT,
        WITHDRAW,
        TERMINATE
    }

    private SolutionStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("方案状态与动作均不能为空");
        }
        switch (action) {
            case SUBMIT:
                requireCurrentIn(current, EngStatusEnum.SOLUTION_DRAFT, "submit");
                break;
            case START_REVIEW:
                requireCurrentIn(current, EngStatusEnum.SOLUTION_SUBMITTED, "startReview");
                break;
            case APPROVE:
            case REJECT:
                requireCurrentIn(current, EngStatusEnum.SOLUTION_IN_REVIEW, action.name());
                break;
            case WITHDRAW:
                requireCurrentIn(current, EngStatusEnum.SOLUTION_SUBMITTED, "withdraw");
                break;
            case TERMINATE:
                requireNotTerminated(current);
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case SUBMIT:
                return EngStatusEnum.SOLUTION_SUBMITTED;
            case START_REVIEW:
                return EngStatusEnum.SOLUTION_IN_REVIEW;
            case APPROVE:
                return EngStatusEnum.SOLUTION_APPROVED;
            case REJECT:
                return EngStatusEnum.SOLUTION_REJECTED;
            case WITHDRAW:
                return EngStatusEnum.SOLUTION_WITHDRAWN;
            case TERMINATE:
                return EngStatusEnum.SOLUTION_TERMINATED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isBaselineLocked(Integer status) {
        return Objects.equals(EngStatusEnum.SOLUTION_APPROVED, status);
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(EngStatusEnum.SOLUTION_APPROVED, status)
                || Objects.equals(EngStatusEnum.SOLUTION_REJECTED, status)
                || Objects.equals(EngStatusEnum.SOLUTION_WITHDRAWN, status)
                || Objects.equals(EngStatusEnum.SOLUTION_TERMINATED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }

    private static void requireNotTerminated(Integer current) {
        if (Objects.equals(EngStatusEnum.SOLUTION_TERMINATED, current)) {
            throw new IllegalStateException("方案已终止，无法执行任何状态变更操作");
        }
    }
}
