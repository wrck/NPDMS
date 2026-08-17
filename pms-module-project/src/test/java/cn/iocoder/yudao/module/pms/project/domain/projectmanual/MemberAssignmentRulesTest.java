package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成员区间规则单测：重叠判定（NULL 至今/闭合区间/边界相接不算重叠）与关闭时点合法性
 */
class MemberAssignmentRulesTest {

    private static final LocalDateTime JAN1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime JAN10 = LocalDateTime.of(2026, 1, 10, 0, 0);
    private static final LocalDateTime JAN20 = LocalDateTime.of(2026, 1, 20, 0, 0);
    private static final LocalDateTime FEB1 = LocalDateTime.of(2026, 2, 1, 0, 0);

    // ========== 重叠判定 ==========

    @Test
    void nullEffectiveToMeansOpenEndedAndOverlapsLaterIntervals() {
        // 旧区间 [JAN1, null) 视为至今：与 [JAN10, JAN20) 重叠
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, null, JAN10, JAN20));
        // 与新的开放区间 [JAN10, null) 重叠
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, null, JAN10, null));
    }

    @Test
    void closedIntervalsOverlapWhenIntersecting() {
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, JAN10, JAN10.minusDays(1), JAN20));
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, JAN10, JAN1.plusDays(1), JAN20));
        // 完全包含
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, FEB1, JAN10, JAN20));
    }

    @Test
    void boundaryTouchingNotCountedAsOverlap() {
        // [JAN1, JAN10] 与 [JAN10, JAN20] 边界相接：不算重叠（关闭旧区间=新区间起点的语义基础）
        assertFalse(MemberAssignmentRules.intervalsOverlap(JAN1, JAN10, JAN10, JAN20));
        assertFalse(MemberAssignmentRules.intervalsOverlap(JAN10, JAN20, JAN1, JAN10));
    }

    @Test
    void disjointIntervalsNotOverlapping() {
        assertFalse(MemberAssignmentRules.intervalsOverlap(JAN1, JAN10, JAN20, FEB1));
        assertFalse(MemberAssignmentRules.intervalsOverlap(JAN20, FEB1, JAN1, JAN10));
    }

    @Test
    void bothOpenIntervalsOverlap() {
        assertTrue(MemberAssignmentRules.intervalsOverlap(null, null, JAN1, null));
        assertTrue(MemberAssignmentRules.intervalsOverlap(JAN1, null, null, null));
    }

    @Test
    void nullEffectiveFromTreatedAsUnboundedPast() {
        // effective_from=NULL 视为无限早：[−∞, JAN10) 与 [JAN1, null) 重叠
        assertTrue(MemberAssignmentRules.intervalsOverlap(null, JAN10, JAN1, null));
        // [−∞, JAN10) 与 [JAN20, FEB1] 不重叠
        assertFalse(MemberAssignmentRules.intervalsOverlap(null, JAN10, JAN20, FEB1));
    }

    // ========== 区间与关闭时点合法性 ==========

    @Test
    void validIntervalRequiresEndNotBeforeStart() {
        assertTrue(MemberAssignmentRules.isValidInterval(JAN1, null));
        assertTrue(MemberAssignmentRules.isValidInterval(null, JAN10));
        assertTrue(MemberAssignmentRules.isValidInterval(JAN1, JAN1));
        assertTrue(MemberAssignmentRules.isValidInterval(JAN1, JAN10));
        assertFalse(MemberAssignmentRules.isValidInterval(JAN10, JAN1.minusDays(1)));
    }

    @Test
    void closeTimeMustNotPrecedeIntervalStart() {
        // 关闭时点 >= effective_from 合法（含相等=零长度区间边界）
        assertTrue(MemberAssignmentRules.canCloseAt(JAN10, JAN1));
        assertTrue(MemberAssignmentRules.canCloseAt(JAN1, JAN1));
        assertTrue(MemberAssignmentRules.canCloseAt(JAN10, null));
        // 关闭时点早于起点非法
        assertFalse(MemberAssignmentRules.canCloseAt(JAN1.minusDays(1), JAN1));
        // 关闭时点为空非法
        assertFalse(MemberAssignmentRules.canCloseAt(null, JAN1));
    }

    @Test
    void newIntervalStartMustNotBeFuture() {
        LocalDateTime now = JAN20;
        // 立即生效与回溯生效合法（effective_from <= now）
        assertTrue(MemberAssignmentRules.canStartIntervalAt(now, now));
        assertTrue(MemberAssignmentRules.canStartIntervalAt(JAN1, now));
        assertTrue(MemberAssignmentRules.canStartIntervalAt(null, now));
        // 未来生效拒绝
        assertFalse(MemberAssignmentRules.canStartIntervalAt(FEB1, now));
    }
}
