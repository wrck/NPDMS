package cn.iocoder.yudao.module.pms.engineering.domain.constructionplan;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 项目工期自然日计算规则。 */
public final class DurationRules {

    public static final String DATE_RANGE = "DATE_RANGE";
    public static final String DURATION_FROM_START = "DURATION_FROM_START";

    private DurationRules() {
    }

    public static ResolvedDuration resolve(String calculationBasisCode, LocalDate startDate,
                                           LocalDate endDate, Integer durationDays) {
        if (DATE_RANGE.equals(calculationBasisCode)) {
            return fromDateRange(startDate, endDate, durationDays);
        }
        if (DURATION_FROM_START.equals(calculationBasisCode)) {
            return fromStartAndDuration(startDate, endDate, durationDays);
        }
        throw new IllegalArgumentException("不支持的工期计算口径");
    }

    private static ResolvedDuration fromDateRange(LocalDate startDate, LocalDate endDate,
                                                  Integer submittedDurationDays) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("起止日期不能为空且结束日期不得早于开始日期");
        }
        final int calculatedDays;
        try {
            calculatedDays = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("工期天数超出允许范围", exception);
        }
        if (calculatedDays <= 0 || submittedDurationDays != null
                && submittedDurationDays != calculatedDays) {
            throw new IllegalArgumentException("提交的工期天数与起止日期不一致");
        }
        return new ResolvedDuration(DATE_RANGE, startDate, endDate, calculatedDays);
    }

    private static ResolvedDuration fromStartAndDuration(LocalDate startDate, LocalDate submittedEndDate,
                                                         Integer durationDays) {
        if (startDate == null || durationDays == null || durationDays <= 0) {
            throw new IllegalArgumentException("开始日期不能为空且工期天数必须为正整数");
        }
        final LocalDate calculatedEndDate;
        try {
            calculatedEndDate = startDate.plusDays((long) durationDays - 1L);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("工期结束日期超出允许范围", exception);
        }
        if (submittedEndDate != null && !submittedEndDate.equals(calculatedEndDate)) {
            throw new IllegalArgumentException("提交的结束日期与工期天数不一致");
        }
        return new ResolvedDuration(DURATION_FROM_START, startDate, calculatedEndDate, durationDays);
    }

    public record ResolvedDuration(
            String calculationBasisCode,
            LocalDate startDate,
            LocalDate endDate,
            Integer durationDays) {
    }

}
