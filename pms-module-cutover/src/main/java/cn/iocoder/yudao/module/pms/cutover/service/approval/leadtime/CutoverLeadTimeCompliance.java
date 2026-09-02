package cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Frozen lead-time compliance fact for one approval instance.
 */
public record CutoverLeadTimeCompliance(String ruleVersion, String timezoneId, String cutoverType,
                                        long scheduledTime, long planSubmittedAt, int requiredDays,
                                        int actualNaturalDays, boolean lateSubmission) {

    public static final String RULE_VERSION = "CUT_LEAD_TIME_R034_V1";
    public static final String TIMEZONE_ID = "Asia/Shanghai";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TIMEZONE_ID);

    public CutoverLeadTimeCompliance {
        require(RULE_VERSION.equals(ruleVersion), "ruleVersion");
        require(TIMEZONE_ID.equals(timezoneId), "timezoneId");
        require(scheduledTime > 0, "scheduledTime");
        require(planSubmittedAt > 0, "planSubmittedAt");
        require(requiredDays == CutoverLeadTimeCalculator.requiredDays(cutoverType), "requiredDays");
        long calculatedDays = ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(planSubmittedAt).atZone(BUSINESS_ZONE).toLocalDate(),
                Instant.ofEpochMilli(scheduledTime).atZone(BUSINESS_ZONE).toLocalDate());
        require(calculatedDays >= Integer.MIN_VALUE && calculatedDays <= Integer.MAX_VALUE, "actualNaturalDays");
        require(actualNaturalDays == (int) calculatedDays, "actualNaturalDays");
        require(lateSubmission == (actualNaturalDays < requiredDays), "lateSubmission");
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }
}
