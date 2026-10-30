package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverRiskMatrixRules;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverSurveyMatrixRules;
import java.util.Map;
import java.util.Set;

/** 与 CUT-03/09/10 的具名业务表对应；请求不能自行声明内置字段为扩展字段。 */
public final class CutoverChecklistBuiltinFields {
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("CUTOVER_BACKGROUND", CutoverSurveyMatrixRules.BACKGROUND_FIELDS),
            Map.entry("DEVICE_LOCATION_PLAN", Set.of("deviceType", "serialNumber", "deviceName", "location")),
            Map.entry("INTERFACE_INTERCONNECT_PLAN", Set.of("localDevice", "localInterface", "remoteInterface",
                    "interfaceType", "cableModel", "remark")),
            Map.entry("IP_VLAN_PLAN", Set.of("deviceName", "interface", "ip", "mask", "vrrpIp", "gateway", "remark")),
            Map.entry("PERFORMANCE_BASELINE", Set.of("serialNumber", "concurrentConnections", "newConnections",
                    "cpuUsage", "memoryUsage")),
            Map.entry("MAJOR_PROJECT_SPARES", Set.of("majorCutover", "spareReference")),
            Map.entry("CURRENT_VERSION_BULLETIN", bulletinFields()),
            Map.entry("TARGET_VERSION_BULLETIN", bulletinFields()));

    private CutoverChecklistBuiltinFields() { }

    private static Set<String> bulletinFields() {
        return Set.of("hasBulletins", "queryBasis", "bulletins", "bulletins.number", "bulletins.subject",
                "bulletins.description", "bulletins.solution", "bulletins.involved", "bulletins.measures");
    }

    public static String semanticKey(FieldContext context) {
        String category = context.businessCategoryCode();
        boolean fixed = "DUAL_MACHINE_CHECK".equals(context.itemTypeCode())
                || CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES.contains(category == null ? "" : category)
                || CutoverSurveyMatrixRules.CORE_SURVEY_CATEGORIES.contains(category == null ? "" : category);
        if (!fixed) return null;
        String family = "DUAL_MACHINE_CHECK".equals(context.itemTypeCode()) ? "DUAL_MACHINE_CHECK" : category;
        if ("$".equals(context.fieldPath())) return family;
        Set<String> fields = FIELDS.getOrDefault(family, Set.of("executed", "normal", "remark"));
        return fields.contains(context.fieldPath()) ? family + "." + context.fieldPath() : null;
    }

    public record FieldContext(String businessCategoryCode, String itemTypeCode, String fieldPath) { }
}
