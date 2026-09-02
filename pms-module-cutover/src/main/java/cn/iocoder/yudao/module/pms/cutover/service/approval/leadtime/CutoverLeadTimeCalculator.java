package cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/**
 * F-CUT-008 immutable lead-time rule calculator.
 */
public final class CutoverLeadTimeCalculator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of(CutoverLeadTimeCompliance.TIMEZONE_ID);
    private static final Set<String> APPLICABLE_GRADES = Set.of("A", "B");
    private static final Map<String, Integer> REQUIRED_DAYS = Map.ofEntries(
            Map.entry("DEVICE_REPLACE_WHOLE", 5),
            Map.entry("DEVICE_REPLACE_BOARD", 3),
            Map.entry("DEVICE_REPLACE_VENDOR", 7),
            Map.entry("DEVICE_ONBOARD", 7),
            Map.entry("VERSION_UPGRADE", 2),
            Map.entry("DISASTER_RECOVERY_DRILL", 2),
            Map.entry("CONFIGURATION_CHANGE", 2),
            Map.entry("NETWORK_TOPOLOGY_CHANGE", 3),
            Map.entry("VERSION_PATCH", 2),
            Map.entry("SIGNATURE_UPGRADE", 1));

    public CutoverLeadTimeCompliance calculate(String grade, String cutoverType,
                                                LocalDateTime scheduledAt, LocalDateTime planSubmittedAt) {
        require(APPLICABLE_GRADES.contains(grade), "grade");
        require(scheduledAt != null, "scheduledAt");
        require(planSubmittedAt != null, "planSubmittedAt");
        int requiredDays = requiredDays(cutoverType);
        long actualDays = ChronoUnit.DAYS.between(planSubmittedAt.toLocalDate(), scheduledAt.toLocalDate());
        require(actualDays >= Integer.MIN_VALUE && actualDays <= Integer.MAX_VALUE, "actualNaturalDays");
        return new CutoverLeadTimeCompliance(
                CutoverLeadTimeCompliance.RULE_VERSION,
                CutoverLeadTimeCompliance.TIMEZONE_ID,
                cutoverType,
                scheduledAt.atZone(BUSINESS_ZONE).toInstant().toEpochMilli(),
                planSubmittedAt.atZone(BUSINESS_ZONE).toInstant().toEpochMilli(),
                requiredDays,
                (int) actualDays,
                actualDays < requiredDays);
    }

    static int requiredDays(String cutoverType) {
        Integer days = REQUIRED_DAYS.get(cutoverType);
        require(days != null, "cutoverType");
        return days;
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }
}
