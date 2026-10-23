package cn.iocoder.yudao.module.pms.cutover.service.task.domain;

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

    public record RevisionTarget(String stage, String status, boolean replaceAssessment, boolean replaceChecklist) { }

    /** 2026-10-15 已确认：审批前修订；审批中须先撤回；批准后不由本入口回退。 */
    public static RevisionTarget revisionTarget(String stage, String status, String grade, String target) {
        boolean beforeApproval = ("P3".equals(stage) && STATUS_SURVEYING.equals(status))
                || ("P4".equals(stage) && STATUS_PLAN_DRAFTING.equals(status))
                || ("P5".equals(stage) && "APPROVING".equals(status));
        if (!beforeApproval) throw new IllegalArgumentException("当前阶段不允许修订前序内容");
        if (STAGE_P2.equals(target)) return new RevisionTarget(STAGE_P2, STATUS_GRADE_CONFIRMING, true, false);
        if (STAGE_P3.equals(target) && Set.of("P4", "P5").contains(stage) && grade != null && Set.of("A", "B", "C").contains(grade)) {
            return new RevisionTarget(STAGE_P3, STATUS_SURVEYING, false, true);
        }
        if (STAGE_P4.equals(target) && "P5".equals(stage)) return new RevisionTarget(STAGE_P4, STATUS_PLAN_DRAFTING, false, false);
        throw new IllegalArgumentException("修订目标不是可返回的已完成步骤");
    }
}
