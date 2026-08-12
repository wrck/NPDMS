package cn.iocoder.yudao.module.pms.engineering.domain;

import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;

import java.util.Objects;

/**
 * 硬件安装状态机规则（FR-ENG-022）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>待安装 → 进行中（start）</li>
 *   <li>进行中 → 已完成（complete）</li>
 *   <li>待安装/进行中 → 异常（markAbnormal）</li>
 * </ul>
 * 终态：已完成、异常。
 */
public final class InstallationStatusRules {

    public enum Action {
        START,
        COMPLETE,
        MARK_ABNORMAL
    }

    private InstallationStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("安装状态与动作均不能为空");
        }
        switch (action) {
            case START:
                requireCurrentIn(current, EngStatusEnum.INSTALLATION_PENDING, "start");
                break;
            case COMPLETE:
                requireCurrentIn(current, EngStatusEnum.INSTALLATION_IN_PROGRESS, "complete");
                break;
            case MARK_ABNORMAL:
                if (!Objects.equals(EngStatusEnum.INSTALLATION_PENDING, current)
                        && !Objects.equals(EngStatusEnum.INSTALLATION_IN_PROGRESS, current)) {
                    throw new IllegalStateException(
                            "动作 markAbnormal 要求当前状态为待安装或进行中，实际为 " + current);
                }
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case START:
                return EngStatusEnum.INSTALLATION_IN_PROGRESS;
            case COMPLETE:
                return EngStatusEnum.INSTALLATION_COMPLETED;
            case MARK_ABNORMAL:
                return EngStatusEnum.INSTALLATION_ABNORMAL;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(EngStatusEnum.INSTALLATION_COMPLETED, status)
                || Objects.equals(EngStatusEnum.INSTALLATION_ABNORMAL, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
