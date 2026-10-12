package cn.iocoder.yudao.module.pms.integration.sync;

/** Explicitly adjudicated mappings only; no truncation or substitution with a parent. */
final class DppmsDepartmentCodeRules {
    private DppmsDepartmentCodeRules() {}
    static String normalize(String code) {
        if (code!=null && code.matches("31120[2-9]|311210")) return "1621"+code.substring(4);
        return code;
    }
    static String parentForNew(String code) {
        if(code.startsWith("16"))return "160000";
        if(code.startsWith("31"))return "310000";
        if("280700".equals(code))return "280000";
        if("999999".equals(code))return null;
        throw new IllegalArgumentException("新部门父项未裁决，不能根据部门编码臆造层级");
    }
}
