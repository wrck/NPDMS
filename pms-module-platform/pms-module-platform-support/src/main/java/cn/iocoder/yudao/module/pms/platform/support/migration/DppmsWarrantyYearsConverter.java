package cn.iocoder.yudao.module.pms.platform.support.migration;

/** Contract warranty is a year count, including zero; source padding is not part of the value. */
public final class DppmsWarrantyYearsConverter {
    public static final String CONVERSION = "DPPMS_WARRANTY_YEARS";
    public static final String RULE_VERSION = "DPPMS_WARRANTY_YEARS_V1";
    private DppmsWarrantyYearsConverter() {}

    public static Integer fromSource(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("DPPMS_WARRANTY_YEARS_INVALID");
        try { return Integer.valueOf(text); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("DPPMS_WARRANTY_YEARS_OUT_OF_RANGE"); }
    }
}
