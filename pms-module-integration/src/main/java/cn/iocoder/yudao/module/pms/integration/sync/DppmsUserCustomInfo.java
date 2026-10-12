package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

/** Only user-approved account and employment properties enter the formal account JSON. */
final class DppmsUserCustomInfo {
    private DppmsUserCustomInfo() {}

    /** Current references must come from verified identities, never from equal numeric IDs. */
    record PersonnelReference(Object sourceEmployeeId, Long userId) {}
    record Employment(Map<String, Object> fields, String companyCode, Long companyId,
                      String departmentCode, Long departmentId, Long postId,
                      Object verifiedSourceEmployeeId, PersonnelReference employee, PersonnelReference reportingEmployee,
                      PersonnelReference functionalReportingEmployee) {}

    static ObjectNode assemble(Map<String, Object> fnd, Map<String, Object> legacy,
                               List<Employment> employments) {
        ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
        if (fnd != null) {
            Object original = require(fnd, "customInfo");
            JsonNode object = original == null ? null : original instanceof JsonNode node ? node
                    : JsonUtils.getObjectMapper().readTree(original.toString());
            if (object != null && !object.isNull() && !object.isObject())
                throw new IllegalArgumentException("原账号customInfo不是完整对象，不能改形或猜配");
            result.set("customInfo", object == null ? JsonUtils.getObjectMapper().nullNode() : object.deepCopy());
            put(result, "emailNotificationEnabled", require(fnd, "isemail"));
            put(result, "effectiveFrom", require(fnd, "effectiveFrom"));
            put(result, "effectiveTo", require(fnd, "effectiveTo"));
            put(result, "defaultPage", require(fnd, "defaultPage"));
            put(result, "passwordExpireTime", require(fnd, "pwdoverdue"));
        }
        if (legacy != null) {
            put(result, "mustChangePassword", require(legacy, "needChangePwd"));
            put(result, "loginErrorCount", require(legacy, "loginErrorCount"));
            put(result, "legacyUserStatus", require(legacy, "status"));
            assertNoBusinessValue(legacy, "userCustom1", "userCustom2", "userCustom5");
        }
        var assignments = result.putArray("employmentAssignments");
        Set<String> ids = new HashSet<>();
        List<Employment> ordered = new ArrayList<>(employments);
        ordered.sort(Comparator.comparing(value -> Objects.toString(require(value.fields(), "id"))));
        for (Employment employment : ordered) {
            Map<String, Object> fields = employment.fields();
            String key = Objects.toString(require(fields, "id"), null);
            if (key == null || !ids.add(key)) throw new IllegalArgumentException("任职资料原身份缺失或重复");
            assertNoBusinessValue(fields, "birthday", "custom1", "custom2");
            ObjectNode assignment = assignments.addObject();
            put(assignment, "employmentRecordKey", key);
            put(assignment, "sourceCompanyId", require(fields, "compID"));
            put(assignment, "companyCode", employment.companyCode());
            put(assignment, "companyId", employment.companyId());
            put(assignment, "sourceDepartmentId", require(fields, "depID"));
            put(assignment, "departmentCode", employment.departmentCode());
            put(assignment, "departmentId", employment.departmentId());
            put(assignment, "sourcePositionId", require(fields, "jobID"));
            put(assignment, "positionCode", fields.get("migrationPositionCode"));
            put(assignment, "postId", employment.postId());
            // t_user_info has no empID column. The account/EHR identity resolver supplies this verified key.
            reference(assignment, "employeeReference", employment.verifiedSourceEmployeeId(), employment.employee());
            reference(assignment, "reportingEmployeeReference", require(fields, "reportTo"), employment.reportingEmployee());
            reference(assignment, "functionalReportingEmployeeReference", require(fields, "wfreportTo"), employment.functionalReportingEmployee());
            put(assignment, "employeeStatus", require(fields, "empStatus"));
            put(assignment, "positionStatus", require(fields, "jobStatus"));
            put(assignment, "employeeType", require(fields, "empType"));
            put(assignment, "historicalEmploymentState", require(fields, "state"));
            // Preserve the original restriction value; no access grant is synthesized here.
            put(assignment, "projectTypeRestrictions", require(fields, "custom4"));
            put(assignment, "englishName", require(fields, "eName"));
            put(assignment, "officePhone", require(fields, "telphone"));
        }
        if (!ordered.isEmpty()) {
            put(result, "englishName", uniqueAttribute(ordered, "eName"));
            put(result, "officePhone", uniqueAttribute(ordered, "telphone"));
        }
        // Match the JSON representation that the Owner persists and reads back. valueToTree
        // keeps small LongNode values, while readTree restores the same JSON numbers as IntNode.
        return (ObjectNode) JsonUtils.getObjectMapper().readTree(result.toString());
    }

    private static Object uniqueAttribute(List<Employment> employments, String name) {
        List<Object> values = employments.stream().map(value -> require(value.fields(), name))
                .filter(value -> value != null && !value.toString().isBlank()).distinct().toList();
        if (values.size() > 1) throw new IllegalArgumentException("多公司任职的" + name + "存在不同值，须裁决账号主资料");
        return values.isEmpty() ? null : values.getFirst();
    }
    private static void reference(ObjectNode node, String property, Object sourceId, PersonnelReference value) {
        if (value != null && !Objects.equals(Objects.toString(sourceId, null), Objects.toString(value.sourceEmployeeId(), null)))
            throw new IllegalArgumentException("已核实人员引用与原任职外键不同");
        ObjectNode reference = node.putObject(property);
        put(reference, "sourceEmployeeId", sourceId);
        put(reference, "userId", value == null ? null : value.userId());
    }
    static void assertNoBusinessValue(Map<String, Object> fields, String... names) {
        for (String name : names) {
            Object value = require(fields, name);
            if (value != null && !value.toString().isBlank() && !"0".equals(value.toString()))
                throw new IllegalArgumentException("已裁决不迁入字段出现业务值，必须重新裁决: " + name);
        }
    }
    private static Object require(Map<String, Object> fields, String name) {
        if (!fields.containsKey(name)) throw new IllegalArgumentException("账号原表字段缺失: " + name);
        return fields.get(name);
    }
    private static void put(ObjectNode node, String name, Object value) {
        if (value == null) node.putNull(name);
        else if (value instanceof JsonNode json) node.set(name, json.deepCopy());
        else node.set(name, JsonUtils.getObjectMapper().valueToTree(value));
    }
}
