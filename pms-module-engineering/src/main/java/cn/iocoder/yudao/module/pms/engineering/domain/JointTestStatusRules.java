package cn.iocoder.yudao.module.pms.engineering.domain;

import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;

import java.util.Objects;

/**
 * 业务联调状态机规则（FR-ENG-024）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>待联调 → 进行中（start）</li>
 *   <li>进行中 → 通过（pass）</li>
 *   <li>进行中 → 失败（fail），失败时必须记录异常或创建问题单</li>
 * </ul>
 * 终态：通过、失败。失败项不能静默通过，必须先关闭异常或创建问题单。
 */
public final class JointTestStatusRules {

    public enum Action {
        START,
        PASS,
        FAIL
    }

    private JointTestStatusRules() {
    }

    public static void requireTransition(Integer current, Action action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("联调状态与动作均不能为空");
        }
        switch (action) {
            case START:
                requireCurrentIn(current, EngStatusEnum.JOINT_TEST_PENDING, "start");
                break;
            case PASS:
                requireCurrentIn(current, EngStatusEnum.JOINT_TEST_IN_PROGRESS, "pass");
                break;
            case FAIL:
                requireCurrentIn(current, EngStatusEnum.JOINT_TEST_IN_PROGRESS, "fail");
                break;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static Integer targetStatus(Action action) {
        switch (action) {
            case START:
                return EngStatusEnum.JOINT_TEST_IN_PROGRESS;
            case PASS:
                return EngStatusEnum.JOINT_TEST_PASSED;
            case FAIL:
                return EngStatusEnum.JOINT_TEST_FAILED;
            default:
                throw new IllegalArgumentException("未知的状态机动作：" + action);
        }
    }

    public static boolean isTerminal(Integer status) {
        return Objects.equals(EngStatusEnum.JOINT_TEST_PASSED, status)
                || Objects.equals(EngStatusEnum.JOINT_TEST_FAILED, status);
    }

    private static void requireCurrentIn(Integer current, Integer expected, String actionName) {
        if (!Objects.equals(expected, current)) {
            throw new IllegalStateException(
                    "动作 " + actionName + " 要求当前状态为 " + expected + "，实际为 " + current);
        }
    }
}
