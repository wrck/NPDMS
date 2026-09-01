package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import java.util.List;
import java.util.Locale;

public final class CutoverApprovalRules {

    public static final List<String> REVIEW_ITEM_CODES = List.of(
            "PREPARATION", "BUSINESS_TEST", "EXECUTION", "ROLLBACK", "OTHER");
    public static final List<String> GRADES = List.of("A", "B", "C", "D");
    public static final List<String> SERVICE_MANAGER_ROLES = List.of(
            "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");
    public static final List<String> ROLE_GROUP_CODES = List.of(
            "CUT_SECOND_LINE_APPROVER", "CUT_RND_APPROVER");

    private CutoverApprovalRules() {
    }

    public static List<String> routeFor(String grade) {
        require(GRADES.contains(grade), "grade");
        return switch (grade) {
            case "A" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND");
            case "B" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE");
            case "C", "D" -> List.of("INITIATOR", "SERVICE_MANAGER");
            default -> throw new IllegalArgumentException("invalid grade");
        };
    }

    public static String requireText(String value, int maxLength, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim())
                && value.length() <= maxLength, field);
        return value;
    }

    public static String comparisonKey(String value) {
        require(value != null && !value.trim().isEmpty(), "identity");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }
}
