package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import java.time.LocalDateTime;

/**
 * 成员角色区间规则（F-PM01 / PM-04 时态关系）
 * <p>
 * 同项目同用户同角色区间不得重叠（effective_to IS NULL 视为至今）；
 * 指派语义=关闭旧区间（effective_to=新区间起点）+ 开启新区间；
 * 留痕前后值由服务层组装，规则类只判重叠与关闭时点合法性。
 */
public final class MemberAssignmentRules {

    /** 成员区间记录状态（区间有效性由 effective_to 表达） */
    public static final String STATUS_ACTIVE = "ACTIVE";

    private MemberAssignmentRules() {
    }

    /**
     * 区间是否重叠（边界相接不算重叠：[a,b] 与 [b,c] 不重叠）。
     * `effective_to=NULL` 视为至今开放（+∞）；`effective_from=NULL` 视为无限早（-∞）。
     */
    public static boolean intervalsOverlap(LocalDateTime from1, LocalDateTime to1,
                                           LocalDateTime from2, LocalDateTime to2) {
        boolean firstStartsBeforeSecondEnds = to2 == null || from1 == null || from1.isBefore(to2);
        boolean secondStartsBeforeFirstEnds = to1 == null || from2 == null || from2.isBefore(to1);
        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }

    /**
     * 区间自身合法性：effective_to 不早于 effective_from（任一端为空视为合法）。
     */
    public static boolean isValidInterval(LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        return effectiveFrom == null || effectiveTo == null || !effectiveTo.isBefore(effectiveFrom);
    }

    /**
     * 关闭时点合法性：关闭时间不得早于区间起点（effective_from &lt;= closeTime）。
     */
    public static boolean canCloseAt(LocalDateTime closeTime, LocalDateTime effectiveFrom) {
        return closeTime != null && (effectiveFrom == null || !closeTime.isBefore(effectiveFrom));
    }

    /**
     * 新区间开启时点合法性：生效起点不得晚于当前时间（回溯生效允许，未来生效拒绝；
     * effective_from &lt; now 等）。
     */
    public static boolean canStartIntervalAt(LocalDateTime effectiveFrom, LocalDateTime now) {
        return effectiveFrom == null || now == null || !effectiveFrom.isAfter(now);
    }
}
