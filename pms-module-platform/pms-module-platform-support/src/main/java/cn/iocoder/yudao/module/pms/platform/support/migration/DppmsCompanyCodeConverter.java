package cn.iocoder.yudao.module.pms.platform.support.migration;

import java.util.Map;

/** Approved DPPMS migration/sync conversion; source evidence keeps the original code. */
public final class DppmsCompanyCodeConverter {
    public static final String CONVERSION = "DPPMS_COMPANY_CODE";
    public static final String RULE_VERSION = "DPPMS_COMPANY_CODE_V1";
    private static final Map<String, Object> VALUES = Map.of(
            "01", "001", "02", "002", "03", "003", "04", "004");

    private DppmsCompanyCodeConverter() {}

    public static String fromSource(String sourceCode) {
        String converted = (String) VALUES.get(sourceCode == null ? "" : sourceCode);
        if (converted == null) {
            throw new IllegalArgumentException("DPPMS_COMPANY_CODE_UNMAPPED: " + sourceCode);
        }
        return converted;
    }

    /** Shared ENUM values for new sync definitions; unknown codes stop before Owner writes. */
    public static Map<String, Object> mappingValues() {
        return VALUES;
    }

}
