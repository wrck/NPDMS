package cn.iocoder.yudao.module.pms.engineering.domain;

import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;

import java.util.Objects;

/**
 * 实施问题状态机规则（FR-ENG-026）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>待处理 → 整改中（startRectify）</li>
 *   <li>整改中 → 待验证（submitForVerify）</li>
 *   <li>待验证 → 已关闭（close，需复测结果）</li>
 *   <li>待验证 → 整改中（reject，验证不通过打回）</li>
 *   <li>任意非终态 → 已挂起（suspend）</li>
 *   <li>已挂起 → 整改中（resume）</li>
 * </ul>
 * 终态：已关闭。未关闭问题阻断验收。
 */
public final class IssueStatusRules {

    public enum Action {
        START_RECTIFY,
        SUBMIT_FOR_VERIFY,
        CLOSE,
        REJECT,
        SUSPEND,
        RESUME
    }

    private IssueStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("问题状态与动作均不能为空");
        }
        switch (action) {
            case START_RECTIFY:
                requireCurrentIn(current, EngStatusEnum.ISSUE_OPEN, "startRectify");
                break;
            case SUBMIT_FOR_VERIFY:
                requireCurrentIn(current, EngStatusEnum.ISSUE_RECTIFYING, "submitForVerify");
                break;
            case CLOSE:
                requireCurrentIn(current, EngStatusEnum.ISSUE_PENDING_VERIFICATION, "close");
                break;
            case REJECT:
                requireCurrentIn(current, EngStatusEnum.ISSUE_PENDING_VERIFICATION, "reject");
                break;
            case SUSPEND:
                if (Objects.equals(EngStatusEnum.ISSUE_CLOSED, current)) {
                    throw new IllegalStateException("问题已关闭，无法挂起");
                }
                break;
            case RESUME:
                requireCurrentIn(current, EngStatusEnum.ISSUE_SUSPENDED, "resume");
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case START_RECTIFY:
                return EngStatusEnum.ISSUE_RECTIFYING;
            case SUBMIT_FOR_VERIFY:
                return EngStatusEnum.ISSUE_PENDING_VERIFICATION;
            case CLOSE:
                return EngStatusEnum.ISSUE_CLOSED;
            case REJECT:
                return EngStatusEnum.ISSUE_RECTIFYING;
            case SUSPEND:
                return EngStatusEnum.ISSUE_SUSPENDED;
            case RESUME:
                return EngStatusEnum.ISSUE_RECTIFYING;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isClosed(Integer status) {
        return Objects.equals(EngStatusEnum.ISSUE_CLOSED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
