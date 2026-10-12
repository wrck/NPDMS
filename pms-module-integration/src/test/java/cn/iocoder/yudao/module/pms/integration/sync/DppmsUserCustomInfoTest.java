package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsUserCustomInfoTest {
    @Test void persistedEmploymentJsonReplaysWithTheSameNodeTypesAndExactUserIds() {
        var previousMapper = JsonUtils.getObjectMapper();
        var module = new tools.jackson.databind.module.SimpleModule();
        module.addSerializer(Long.class, cn.iocoder.yudao.framework.common.util.json.databind.NumberSerializer.INSTANCE);
        JsonUtils.init(tools.jackson.databind.json.JsonMapper.builder().addModule(module).build());
        try {
            var small = employment(10, 1);
            var large = new DppmsUserCustomInfo.Employment(small.fields(), small.companyCode(), small.companyId(),
                    small.departmentCode(), small.departmentId(), small.postId(), small.verifiedSourceEmployeeId(),
                    new DppmsUserCustomInfo.PersonnelReference(101, 2108554375892738049L), small.reportingEmployee(), null);
            var result = DppmsUserCustomInfo.assemble(fnd(), legacy(), List.of(small));
            assertEquals(JsonUtils.parseTree(result.toString()), result);
            var largeResult = DppmsUserCustomInfo.assemble(fnd(), legacy(), List.of(large));
            assertEquals(JsonUtils.parseTree(largeResult.toString()), largeResult);
            assertEquals("2108554375892738049", largeResult.path("employmentAssignments").get(0).path("employeeReference").path("userId").asString());
            assertEquals(1, largeResult.path("employmentAssignments").get(0).path("companyId").asInt());
            assertTrue(largeResult.path("customInfo").path("arbitrary").path("value").isNull());
        } finally {
            JsonUtils.init(previousMapper);
        }
    }
    private Map<String, Object> fnd() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("customInfo", "{\"phone\":\"example\",\"disableFuzzyQueryProjectByProjectName\":false,\"arbitrary\":{\"value\":null}}");
        values.put("isemail", 1); values.put("effectiveFrom", "2016-01-01T00:00:00");
        values.put("effectiveTo", null); values.put("defaultPage", "/legacy"); values.put("pwdoverdue", null);
        return values;
    }
    private Map<String, Object> legacy() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("needChangePwd", false); values.put("loginErrorCount", 0); values.put("status", 2);
        values.put("userCustom1", null); values.put("userCustom2", ""); values.put("userCustom5", 0);
        return values;
    }
    private DppmsUserCustomInfo.Employment employment(int id, int company) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("compID", company); values.put("depID", 70); values.put("jobID", 8);
        values.put("reportTo", 202); values.put("wfreportTo", null);
        values.put("empStatus", 1); values.put("jobStatus", null); values.put("empType", 1); values.put("state", 2);
        values.put("custom4", ";A;B;"); values.put("eName", "Example"); values.put("telphone", null);
        values.put("birthday", null); values.put("custom1", 0); values.put("custom2", null);
        return new DppmsUserCustomInfo.Employment(values, company == 1 ? "001" : "002", (long) company,
                "050000", 70L, null, 101, new DppmsUserCustomInfo.PersonnelReference(101, 9001L),
                new DppmsUserCustomInfo.PersonnelReference(202, null), null);
    }
    @Test void originalObjectAndNullFalseZeroSurviveWithoutGrantOrCredentialProperties() {
        var result = DppmsUserCustomInfo.assemble(fnd(), legacy(), List.of());
        var serialized = JsonUtils.parseTree(JsonUtils.toJsonString(result));
        assertTrue(serialized.path("customInfo").isObject());
        assertFalse(serialized.path("customInfo").path("disableFuzzyQueryProjectByProjectName").asBoolean());
        assertTrue(serialized.path("customInfo").path("arbitrary").has("value"));
        assertTrue(serialized.path("customInfo").path("arbitrary").path("value").isNull());
        assertTrue(serialized.has("effectiveTo")); assertTrue(serialized.path("effectiveTo").isNull());
        assertFalse(serialized.path("mustChangePassword").asBoolean()); assertEquals(0, serialized.path("loginErrorCount").asInt());
        assertEquals(2, serialized.path("legacyUserStatus").asInt());
        assertFalse(serialized.has("password")); assertFalse(serialized.has("roles")); assertFalse(serialized.has("projectGrants"));
    }
    @Test void companyAssignmentsAndVerifiedOrUnresolvedPersonnelReferencesStayDistinct() {
        var result = DppmsUserCustomInfo.assemble(null, legacy(), List.of(employment(20, 2), employment(10, 1)));
        var assignments = result.path("employmentAssignments");
        assertEquals(2, assignments.size());
        assertEquals("001", assignments.get(0).path("companyCode").asString());
        assertEquals("002", assignments.get(1).path("companyCode").asString());
        assertEquals(9001, assignments.get(0).path("employeeReference").path("userId").asInt());
        assertEquals(202, assignments.get(0).path("reportingEmployeeReference").path("sourceEmployeeId").asInt());
        assertTrue(assignments.get(0).path("reportingEmployeeReference").path("userId").isNull());
        assertEquals(";A;B;", assignments.get(0).path("projectTypeRestrictions").asString());
        assertEquals(2, assignments.get(0).path("historicalEmploymentState").asInt());
        assertTrue(assignments.get(0).path("positionStatus").isNull());
    }
    @Test void futureReservedValuesOrMissingOriginalColumnsBlockInsteadOfSilentlyDropping() {
        var values = legacy(); values.put("userCustom5", 19);
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, values, List.of()));
        var missing = legacy(); missing.remove("needChangePwd");
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, missing, List.of()));
        var assignment = employment(10, 1); assignment.fields().put("birthday", "1980-01-01");
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, legacy(), List.of(assignment)));
    }
    @Test void conflictingCompanyContactsAndFalsePersonnelReferencesRequireAdjudication() {
        var first = employment(10, 1); var second = employment(20, 2); second.fields().put("eName", "Different");
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, legacy(), List.of(first, second)));
        first.fields().put("reportTo", 203);
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, legacy(), List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> DppmsUserCustomInfo.assemble(null, legacy(), List.of(second, second)));
    }
}
