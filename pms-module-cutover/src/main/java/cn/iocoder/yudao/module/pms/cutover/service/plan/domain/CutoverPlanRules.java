package cn.iocoder.yudao.module.pms.cutover.service.plan.domain;

import java.util.List;

public final class CutoverPlanRules {

    public static final List<String> STANDARD_SECTIONS = List.of(
            "PRE_OPERATION", "OPERATION", "CLOSING_COLLECTION",
            "POST_BUSINESS_TEST", "ROLLBACK", "POST_CUTOVER_SUPPORT");
    public static final List<String> SIMPLE_SECTIONS = List.of("OPERATION", "ROLLBACK");
    public static final List<String> SUPPORT_ROLES = List.of(
            "CUSTOMER", "DP_FIRST_LINE", "DP_SECOND_LINE", "DP_RND");

    private CutoverPlanRules() {
    }

    public static String comparisonKey(String value) {
        require(value != null && !value.trim().isEmpty(), "identity");
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static void require(boolean condition, String field) {
        if (!condition) throw new IllegalArgumentException("invalid " + field);
    }
}
