package cn.iocoder.yudao.module.pms.project.domain.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态机规则（FR-PROJ-004 / T-V1-PROJ-006）。
 * <p>
 * 状态取值：0 草稿、1 待处理、2 进行中、3 受阻、4 待验证、5 已完成、6 已取消。
 * <p>
 * 合法迁移：
 * <ul>
 *   <li>0 草稿 → 1 待处理</li>
 *   <li>1 待处理 → 2 进行中 / 6 已取消</li>
 *   <li>2 进行中 → 3 受阻 / 4 待验证 / 5 已完成 / 6 已取消</li>
 *   <li>3 受阻 → 2 进行中 / 6 已取消</li>
 *   <li>4 待验证 → 2 进行中（驳回返工）/ 5 已完成 / 6 已取消</li>
 * </ul>
 * 终态：5 已完成、6 已取消。
 */
public final class TaskStatusRules {

    /** 0 草稿 */
    public static final int DRAFT = 0;
    /** 1 待处理 */
    public static final int PENDING = 1;
    /** 2 进行中 */
    public static final int IN_PROGRESS = 2;
    /** 3 受阻 */
    public static final int BLOCKED = 3;
    /** 4 待验证 */
    public static final int TO_VERIFY = 4;
    /** 5 已完成 */
    public static final int COMPLETED = 5;
    /** 6 已取消 */
    public static final int CANCELLED = 6;

    /**
     * 终态集合：已完成、已取消。
     */
    private static final Set<Integer> TERMINAL = new HashSet<>();
    static {
        TERMINAL.add(COMPLETED);
        TERMINAL.add(CANCELLED);
    }

    /**
     * 合法迁移图：key 为源状态，value 为可迁移目标状态集合。
     */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(DRAFT, Set.of(PENDING));
        TRANSITIONS.put(PENDING, Set.of(IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(IN_PROGRESS, Set.of(BLOCKED, TO_VERIFY, COMPLETED, CANCELLED));
        TRANSITIONS.put(BLOCKED, Set.of(IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(TO_VERIFY, Set.of(IN_PROGRESS, COMPLETED, CANCELLED));
        // 终态无可迁移
        TRANSITIONS.put(COMPLETED, Set.of());
        TRANSITIONS.put(CANCELLED, Set.of());
    }

    private TaskStatusRules() {
    }

    /**
     * 校验状态迁移合法性。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return {@code true} 合法
     */
    public static boolean canTransition(int from, int to) {
        Set<Integer> targets = TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    /**
     * 校验状态迁移合法性，非法时抛出非法参数异常。
     */
    public static void requireTransition(int from, int to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("任务状态不允许从 " + from + " 迁移到 " + to);
        }
    }

    /**
     * 是否为终态。
     */
    public static boolean isTerminal(int status) {
        return TERMINAL.contains(status);
    }

    /**
     * 是否为已完成。
     */
    public static boolean isCompleted(int status) {
        return status == COMPLETED;
    }

    /**
     * 是否为已取消。
     */
    public static boolean isCancelled(int status) {
        return status == CANCELLED;
    }

    /**
     * 是否为完成态（已完成或已取消），用于门禁判断。
     */
    public static boolean isFinished(int status) {
        return isCompleted(status) || isCancelled(status);
    }
}
