package cn.iocoder.yudao.module.pms.project.domain.risk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 风险状态机规则（FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 状态取值：0 已识别、1 处理中、2 已关闭、3 已发生。
 * <p>
 * 合法迁移：
 * <ul>
 *   <li>0 已识别 → 1 处理中</li>
 *   <li>1 处理中 → 2 已关闭 / 3 已发生</li>
 *   <li>3 已发生 → 2 已关闭</li>
 * </ul>
 * 终态：2 已关闭。
 */
public final class RiskStatusRules {

    /** 0 已识别 */
    public static final int IDENTIFIED = 0;
    /** 1 处理中 */
    public static final int IN_PROGRESS = 1;
    /** 2 已关闭 */
    public static final int CLOSED = 2;
    /** 3 已发生 */
    public static final int OCCURRED = 3;

    /**
     * 终态集合：已关闭。
     */
    private static final Set<Integer> TERMINAL = new HashSet<>();
    static {
        TERMINAL.add(CLOSED);
    }

    /**
     * 合法迁移图：key 为源状态，value 为可迁移目标状态集合。
     */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(IDENTIFIED, Set.of(IN_PROGRESS));
        TRANSITIONS.put(IN_PROGRESS, Set.of(CLOSED, OCCURRED));
        TRANSITIONS.put(OCCURRED, Set.of(CLOSED));
        // 终态无可迁移
        TRANSITIONS.put(CLOSED, Set.of());
    }

    private RiskStatusRules() {
    }

    /**
     * 校验状态迁移合法性。
     */
    public static boolean canTransition(int from, int to) {
        Set<Integer> targets = TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    /**
     * 校验状态迁移合法性，非法时抛出非法状态异常。
     */
    public static void requireTransition(int from, int to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("风险状态不允许从 " + from + " 迁移到 " + to);
        }
    }

    /**
     * 是否为终态。
     */
    public static boolean isTerminal(int status) {
        return TERMINAL.contains(status);
    }

    /**
     * 是否为已关闭。
     */
    public static boolean isClosed(int status) {
        return status == CLOSED;
    }
}
