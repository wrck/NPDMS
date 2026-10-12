package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.Map;

/** User-adjudicated business values, converted from legacy constant semantics; not literal constant names. */
final class DppmsRoleCodes {
    private DppmsRoleCodes() {}
    static final Map<String, String> T_SOURCE_CODES = Map.ofEntries(
            Map.entry("1", "admin"), Map.entry("2", "user"), Map.entry("5", "projectMember"),
            Map.entry("6", "projectAdmin"), Map.entry("7", "projectManager"), Map.entry("8", "projectAFQC"),
            Map.entry("9", "projectYFQC"), Map.entry("10", "projectSubAdmin"), Map.entry("11", "projectAreaManager"),
            Map.entry("12", "projectPreSales"), Map.entry("13", "userManager"), Map.entry("14", "financialAP"),
            Map.entry("15", "dataViewer"), Map.entry("16", "dispatchSettleStaff"));
    static final Map<String, Object> T_CODES = Map.ofEntries(
            Map.entry("admin", "super_admin"), Map.entry("user", "user"),
            Map.entry("projectMember", "projectMember"), Map.entry("projectAdmin", "projectAdmin"),
            Map.entry("projectManager", "PROJECT_MANAGER"), Map.entry("projectAFQC", "projectAFQC"),
            Map.entry("projectYFQC", "projectYFQC"), Map.entry("projectSubAdmin", "projectSubAdmin"),
            Map.entry("projectAreaManager", "projectAreaManager"), Map.entry("projectPreSales", "projectPreSales"),
            Map.entry("userManager", "userManager"), Map.entry("financialAP", "financialAP"),
            Map.entry("dataViewer", "dataViewer"), Map.entry("dispatchSettleStaff", "dispatchSettleStaff"));
    static final Map<String, Object> FND_CODES = Map.ofEntries(
            Map.entry("1", "admin"), Map.entry("3", "common"), Map.entry("5", "projectAdmin"),
            Map.entry("6", "projectViewer"), Map.entry("9", "areaLeader"), Map.entry("10", "engineeManagerLeader"),
            Map.entry("11", "serviceManager"), Map.entry("12", "programManager"), Map.entry("13", "engineeManager"),
            Map.entry("14", "callbackPer"), Map.entry("15", "salesPeople"), Map.entry("16", "financialStaff"),
            Map.entry("17", "presalesStaff"), Map.entry("18", "probAdmin"), Map.entry("19", "probSupporter"),
            Map.entry("20", "probRD"), Map.entry("21", "warrantyCallbacker"), Map.entry("22", "componentAdmin"));
    static String targetFnd(String convertedCode) {
        return switch (convertedCode) {
            case "common" -> "user";
            case "projectAdmin" -> "projectAdmin";
            case "serviceManager" -> "SERVICE_MANAGER";
            case "programManager" -> "PROJECT_MANAGER";
            default -> convertedCode;
        };
    }
    static boolean reference(String object, String sourceCode) {
        return "T_ROLE".equals(object) ? java.util.Set.of("admin", "projectManager").contains(sourceCode)
                : java.util.Set.of("common", "projectAdmin", "serviceManager", "programManager").contains(sourceCode);
    }
}
