package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CutoverBuiltinResultCodecTest {
    @Test void backgroundPreservesAllSixFieldsIncludingFalse() {
        var input = Map.<String, Object>of("solvesOnlineIssue", false, "issueTicketNo", "INC-42",
                "issueHandler", "fixture-handler", "repeatCutover", false,
                "firstCutoverOwner", "fixture-owner", "backgroundDescription", "sample background");
        var row = CutoverBuiltinResultCodec.cutoverBackground(write(input));
        assertEquals(input, CutoverBuiltinResultCodec.value(row));
        assertEquals(7L, row.getTenantId());
        assertEquals(100L, row.getChecklistResultId());
    }

    @Test void ipPlanPreservesAllSevenColumns() {
        var input = Map.<String, Object>of("deviceName", "fixture-device", "interface", "eth0",
                "ip", "192.0.2.2", "mask", "255.255.255.0", "vrrpIp", "192.0.2.3",
                "gateway", "192.0.2.1", "remark", "sample");
        var row = CutoverBuiltinResultCodec.ipPlan(table("IP_VLAN_PLAN", null, input, false));
        assertEquals(input, CutoverBuiltinResultCodec.value(row));
        assertNull(row.getExtensionValues());
    }

    @Test void performancePreservesFractionalDecimalValues() {
        var input = Map.<String, Object>of("serialNumber", "QA-SN", "concurrentConnections", new BigDecimal("12.75"),
                "newConnections", new BigDecimal("3.125"), "cpuUsage", new BigDecimal("42.625"),
                "memoryUsage", new BigDecimal("70.5"));
        var row = CutoverBuiltinResultCodec.performance(table("PERFORMANCE_BASELINE", null, input, false));
        assertEquals(0, row.getCpuUsage().compareTo(new BigDecimal("42.625")));
        assertEquals(input, CutoverBuiltinResultCodec.value(row));
        assertNull(row.getExtensionValues());
    }

    @Test void bulletinPreservesSixColumnsFalseAndEmptyStrings() {
        var input = Map.<String, Object>of("number", "TB-42", "subject", "sample", "description", "",
                "solution", "", "involved", false, "measures", "");
        var row = CutoverBuiltinResultCodec.technicalBulletin(table("CURRENT_VERSION_BULLETIN", "bulletins", input, false));
        assertEquals(input, CutoverBuiltinResultCodec.value(row));
        assertNull(row.getExtensionValues());
    }

    @Test void onlyDeclaredCustomColumnEntersExtensions() {
        var input = Map.<String, Object>of("deviceName", "fixture-device", "interface", "eth0", "ip", "192.0.2.2",
                "mask", "255.255.255.0", "vrrpIp", "", "gateway", "192.0.2.1", "remark", "",
                "customNote", "extension");
        var row = CutoverBuiltinResultCodec.ipPlan(table("IP_VLAN_PLAN", null, input, true));
        assertEquals(Map.of("customNote", "extension"), JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        assertEquals(input, CutoverBuiltinResultCodec.value(row));
    }

    @Test void undeclaredColumnsAndNumericBooleansAreRejected() {
        var undeclared = table("IP_VLAN_PLAN", null, Map.of("ip", "192.0.2.2"), false);
        assertThrows(IllegalArgumentException.class, () -> CutoverBuiltinResultCodec.ipPlan(
                new CutoverBuiltinResultCodec.TableWrite(undeclared.result(), JsonUtils.parseTree("{\"ip\":\"192.0.2.2\",\"unknown\":\"x\"}"),
                        undeclared.definition(), undeclared.tableFieldId(), 0)));
        assertThrows(IllegalArgumentException.class, () -> CutoverBuiltinResultCodec.cutoverBackground(write(Map.of("solvesOnlineIssue", 0))));
        assertThrows(IllegalArgumentException.class, () -> CutoverBuiltinResultCodec.technicalBulletin(
                table("CURRENT_VERSION_BULLETIN", "bulletins", Map.of("involved", 1), false)));
    }

    @Test void remainingFixedAnswersPreserveTheirPublicValues() {
        assertEquals("sample", CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.businessSummary(write("sample"))));
        assertEquals("LOCAL", CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.impactScope(write("LOCAL"))));
        assertEquals("CONTINUOUS", CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.continuityRequirement(write("CONTINUOUS"))));
        assertEquals(new BigDecimal("2.5"), CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.interruption(write(new BigDecimal("2.5")))));
        var feedback = Map.<String, Object>of("normal", false, "executed", true, "remark", "");
        assertEquals(feedback, CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.checkFeedback(write(feedback))));
        var spare = Map.<String, Object>of("majorCutover", false, "spareReference", "");
        assertEquals(spare, CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.spareReference(write(spare))));
        var bulletin = Map.<String, Object>of("hasBulletins", false, "queryBasis", "");
        assertEquals(bulletin, CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.bulletinSummary(write(bulletin))));
        var location = Map.<String, Object>of("deviceType", "FW", "serialNumber", "QA-SN", "deviceName", "sample", "location", "rack");
        assertEquals(location, CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.deviceLocation(table("DEVICE_LOCATION_PLAN", null, location, false))));
        var interfaces = Map.<String, Object>of("localDevice", "sample", "localInterface", "eth0", "remoteInterface", "eth1",
                "interfaceType", "GE", "cableModel", "sample-cable", "remark", "");
        assertEquals(interfaces, CutoverBuiltinResultCodec.value(CutoverBuiltinResultCodec.interfacePlan(table("INTERFACE_INTERCONNECT_PLAN", null, interfaces, false))));
    }

    private static CutoverBuiltinResultCodec.Write write(Object value) {
        var result = new CutoverChecklistItemResultDO(); result.setId(100L); result.setTenantId(7L);
        return new CutoverBuiltinResultCodec.Write(result, JsonUtils.parseTree(JsonUtils.toJsonString(value)));
    }

    private static CutoverBuiltinResultCodec.TableWrite table(String category, String tableCode,
                                                               Map<String, Object> input, boolean custom) {
        List<String> fixed = switch (category) {
            case "DEVICE_LOCATION_PLAN" -> List.of("deviceType", "serialNumber", "deviceName", "location");
            case "INTERFACE_INTERCONNECT_PLAN" -> List.of("localDevice", "localInterface", "remoteInterface", "interfaceType", "cableModel", "remark");
            case "IP_VLAN_PLAN" -> List.of("deviceName", "interface", "ip", "mask", "vrrpIp", "gateway", "remark");
            case "PERFORMANCE_BASELINE" -> List.of("serialNumber", "concurrentConnections", "newConnections", "cpuUsage", "memoryUsage");
            default -> List.of("number", "subject", "description", "solution", "involved", "measures");
        };
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String code : fixed) columns.add(Map.of("code", code, "type", "involved".equals(code) ? "BOOLEAN" : "TEXT"));
        if (custom) columns.add(Map.of("code", "customNote", "type", "TEXT"));
        Map<String, Object> schema = tableCode == null ? Map.of("type", "TABLE", "columns", columns)
                : Map.of("type", "OBJECT", "fields", List.of(Map.of("code", tableCode, "type", "TABLE", "columns", columns)));
        var definition = CutoverFieldSchemaCodec.encode(CutoverFieldSchemaCodec.Write.builder().tenantId(7L)
                .itemDefinitionRevisionId(10L).businessCategoryCode(category).itemTypeCode("BUSINESS_SURVEY").schema(schema).build());
        var field = definition.fields().stream().filter(row -> tableCode == null ? row.getParentFieldId() == null
                : tableCode.equals(row.getFieldCode())).findFirst().orElseThrow();
        var value = write(input);
        return new CutoverBuiltinResultCodec.TableWrite(value.result(), value.value(), definition, field.getId(), 0);
    }
}
