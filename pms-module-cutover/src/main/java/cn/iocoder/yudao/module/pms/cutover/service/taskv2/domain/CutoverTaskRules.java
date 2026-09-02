package cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain;

import java.util.Locale;
import java.util.Set;

/** F-CUT-002 P1/P2 最小状态规则。 */
public final class CutoverTaskRules {

    public static final String ORIGIN_NEW_PLATFORM = "NEW_PLATFORM";
    public static final String STAGE_P1 = "P1";
    public static final String STAGE_P2 = "P2";
    public static final String STAGE_P3 = "P3";
    public static final String STAGE_P4 = "P4";
    public static final String STATUS_GRADE_CONFIRMING = "GRADE_CONFIRMING";
    public static final String STATUS_SURVEYING = "SURVEYING";
    public static final String STATUS_PLAN_DRAFTING = "PLAN_DRAFTING";
    public static final String ASSESSMENT_DRAFT = "DRAFT";
    public static final String ASSESSMENT_SUBMITTED = "SUBMITTED";
    public static final String TEMPLATE_CODE = "CUT_P2_MANUAL_ASSESSMENT";
    public static final long TEMPLATE_VERSION = 1L;

    private static final Set<String> GRADES = Set.of("A", "B", "C", "D");

    private CutoverTaskRules() {
    }

    public static String normalizeGrade(String grade) {
        String normalized = normalizeRequired(grade, "manualGrade", 8).toUpperCase(Locale.ROOT);
        if (!GRADES.contains(normalized)) {
            throw new IllegalArgumentException("manualGrade必须为A/B/C/D");
        }
        return normalized;
    }

    public static SubmissionTarget submissionTarget(String grade) {
        String normalized = normalizeGrade(grade);
        return "D".equals(normalized)
                ? new SubmissionTarget(STAGE_P4, STATUS_PLAN_DRAFTING, true)
                : new SubmissionTarget(STAGE_P3, STATUS_SURVEYING, false);
    }

    public static String normalizeRequired(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        String normalized = value.trim();
        if (!normalized.equals(value) || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }

    public record SubmissionTarget(String stage, String status, boolean simpleFlow) {
    }
}
