package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** HTTP 答案与具名业务列的映射。固定业务答案不会写入扩展 JSON。 */
public final class CutoverBuiltinResultCodec {
    private CutoverBuiltinResultCodec() { }
    public record Write(CutoverChecklistItemResultDO result, JsonNode value) { }
    public record TableWrite(CutoverChecklistItemResultDO result, JsonNode value,
                             CutoverFieldSchemaCodec.Definition definition, Long tableFieldId, int rowNo) { }

    public static CutoverResultCutoverBackgroundDO cutoverBackground(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultCutoverBackgroundDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .solvesOnlineIssueFlag(bool(value.get("solvesOnlineIssue")))
                .issueTicketNo(text(value.get("issueTicketNo")))
                .issueHandler(text(value.get("issueHandler")))
                .repeatCutoverFlag(bool(value.get("repeatCutover")))
                .firstCutoverOwner(text(value.get("firstCutoverOwner")))
                .backgroundDescription(text(value.get("backgroundDescription")))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultCutoverBackgroundDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "solvesOnlineIssue", row.getSolvesOnlineIssueFlag());
        put(value, "issueTicketNo", row.getIssueTicketNo());
        put(value, "issueHandler", row.getIssueHandler());
        put(value, "repeatCutover", row.getRepeatCutoverFlag());
        put(value, "firstCutoverOwner", row.getFirstCutoverOwner());
        put(value, "backgroundDescription", row.getBackgroundDescription());
        return value;
    }

    public static CutoverResultBusinessSummaryDO businessSummary(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultBusinessSummaryDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .businessDescription(text(value))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultBusinessSummaryDO row) {
        if (row == null) return null;
        return row.getBusinessDescription();
    }

    public static CutoverResultImpactScopeDO impactScope(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultImpactScopeDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .impactScopeCode(text(value))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultImpactScopeDO row) {
        if (row == null) return null;
        return row.getImpactScopeCode();
    }

    public static CutoverResultContinuityRequirementDO continuityRequirement(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultContinuityRequirementDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .continuityRequirementCode(text(value))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultContinuityRequirementDO row) {
        if (row == null) return null;
        return row.getContinuityRequirementCode();
    }

    public static CutoverResultInterruptionDO interruption(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultInterruptionDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .interruptionCount(number(value))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultInterruptionDO row) {
        if (row == null) return null;
        return row.getInterruptionCount();
    }

    public static CutoverResultCheckFeedbackDO checkFeedback(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultCheckFeedbackDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .checkNormalFlag(bool(value.get("normal")))
                .executedFlag(bool(value.get("executed")))
                .remark(text(value.get("remark")))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultCheckFeedbackDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "normal", row.getCheckNormalFlag());
        put(value, "executed", row.getExecutedFlag());
        put(value, "remark", row.getRemark());
        return value;
    }

    public static CutoverResultSpareReferenceDO spareReference(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultSpareReferenceDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .majorCutoverFlag(bool(value.get("majorCutover")))
                .spareReferenceText(text(value.get("spareReference")))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultSpareReferenceDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "majorCutover", row.getMajorCutoverFlag());
        put(value, "spareReference", row.getSpareReferenceText());
        return value;
    }

    public static CutoverResultBulletinSummaryDO bulletinSummary(Write write) {
        var root = write.result(); var value = write.value();
        return CutoverResultBulletinSummaryDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .hasBulletinsFlag(bool(value.get("hasBulletins")))
                .manualQueryBasis(text(value.get("queryBasis")))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultBulletinSummaryDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "hasBulletins", row.getHasBulletinsFlag());
        put(value, "queryBasis", row.getManualQueryBasis());
        return value;
    }

    public static CutoverResultDeviceLocationDO deviceLocation(TableWrite write) {
        var root = write.result(); var value = write.value();
        return CutoverResultDeviceLocationDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .tableFieldDefinitionId(write.tableFieldId()).rowNo(write.rowNo())
                .deviceType(text(value.get("deviceType")))
                .sn(text(value.get("serialNumber")))
                .deviceName(text(value.get("deviceName")))
                .location(text(value.get("location")))
                .extensionValues(extensions(write))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultDeviceLocationDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "deviceType", row.getDeviceType());
        put(value, "serialNumber", row.getSn());
        put(value, "deviceName", row.getDeviceName());
        put(value, "location", row.getLocation());
        if (row.getExtensionValues() != null) value.putAll(JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        return value;
    }

    public static CutoverResultInterfacePlanDO interfacePlan(TableWrite write) {
        var root = write.result(); var value = write.value();
        return CutoverResultInterfacePlanDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .tableFieldDefinitionId(write.tableFieldId()).rowNo(write.rowNo())
                .localDevice(text(value.get("localDevice")))
                .localInterface(text(value.get("localInterface")))
                .remoteInterface(text(value.get("remoteInterface")))
                .interfaceType(text(value.get("interfaceType")))
                .cableModel(text(value.get("cableModel")))
                .remark(text(value.get("remark")))
                .extensionValues(extensions(write))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultInterfacePlanDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "localDevice", row.getLocalDevice());
        put(value, "localInterface", row.getLocalInterface());
        put(value, "remoteInterface", row.getRemoteInterface());
        put(value, "interfaceType", row.getInterfaceType());
        put(value, "cableModel", row.getCableModel());
        put(value, "remark", row.getRemark());
        if (row.getExtensionValues() != null) value.putAll(JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        return value;
    }

    public static CutoverResultIpPlanDO ipPlan(TableWrite write) {
        var root = write.result(); var value = write.value();
        return CutoverResultIpPlanDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .tableFieldDefinitionId(write.tableFieldId()).rowNo(write.rowNo())
                .deviceName(text(value.get("deviceName")))
                .interfaceName(text(value.get("interface")))
                .ipAddress(text(value.get("ip")))
                .subnetMask(text(value.get("mask")))
                .vrrpIp(text(value.get("vrrpIp")))
                .gateway(text(value.get("gateway")))
                .remark(text(value.get("remark")))
                .extensionValues(extensions(write))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultIpPlanDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "deviceName", row.getDeviceName());
        put(value, "interface", row.getInterfaceName());
        put(value, "ip", row.getIpAddress());
        put(value, "mask", row.getSubnetMask());
        put(value, "vrrpIp", row.getVrrpIp());
        put(value, "gateway", row.getGateway());
        put(value, "remark", row.getRemark());
        if (row.getExtensionValues() != null) value.putAll(JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        return value;
    }

    public static CutoverResultPerformanceDO performance(TableWrite write) {
        var root = write.result(); var value = write.value();
        return CutoverResultPerformanceDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .tableFieldDefinitionId(write.tableFieldId()).rowNo(write.rowNo())
                .sn(text(value.get("serialNumber")))
                .concurrentConnections(number(value.get("concurrentConnections")))
                .newConnections(number(value.get("newConnections")))
                .cpuUsage(number(value.get("cpuUsage")))
                .memoryUsage(number(value.get("memoryUsage")))
                .extensionValues(extensions(write))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultPerformanceDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "serialNumber", row.getSn());
        put(value, "concurrentConnections", row.getConcurrentConnections());
        put(value, "newConnections", row.getNewConnections());
        put(value, "cpuUsage", row.getCpuUsage());
        put(value, "memoryUsage", row.getMemoryUsage());
        if (row.getExtensionValues() != null) value.putAll(JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        return value;
    }

    public static CutoverResultTechnicalBulletinDO technicalBulletin(TableWrite write) {
        var root = write.result(); var value = write.value();
        return CutoverResultTechnicalBulletinDO.builder().tenantId(root.getTenantId()).checklistResultId(root.getId())
                .tableFieldDefinitionId(write.tableFieldId()).rowNo(write.rowNo())
                .bulletinNumber(text(value.get("number")))
                .bulletinSubject(text(value.get("subject")))
                .bulletinDescription(text(value.get("description")))
                .bulletinSolution(text(value.get("solution")))
                .involvedFlag(bool(value.get("involved")))
                .measures(text(value.get("measures")))
                .extensionValues(extensions(write))
                .createUserId(root.getCreatedBy()).createTime(root.getCreatedAt()).build();
    }

    public static Object value(CutoverResultTechnicalBulletinDO row) {
        if (row == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "number", row.getBulletinNumber());
        put(value, "subject", row.getBulletinSubject());
        put(value, "description", row.getBulletinDescription());
        put(value, "solution", row.getBulletinSolution());
        put(value, "involved", row.getInvolvedFlag());
        put(value, "measures", row.getMeasures());
        if (row.getExtensionValues() != null) value.putAll(JsonUtils.parseObject(row.getExtensionValues(), Map.class));
        return value;
    }

    private static String extensions(TableWrite write) {
        if (!write.value().isObject()) throw new IllegalArgumentException("业务表格行必须是对象");
        var columns = write.definition().fields().stream()
                .filter(field -> Objects.equals(write.tableFieldId(), field.getParentFieldId())).toList();
        Set<String> known = new HashSet<>();
        Map<String, Object> extension = new LinkedHashMap<>();
        for (var column : columns) {
            known.add(column.getFieldCode());
            if (column.getExtensionFieldKey() == null || !write.value().has(column.getFieldCode())) continue;
            if ("FILE".equals(column.getDataTypeCode()) || "TABLE".equals(column.getDataTypeCode())) {
                throw new IllegalArgumentException("固定业务表的扩展列不支持文件或嵌套表格关系");
            }
            extension.put(column.getExtensionFieldKey(), JsonUtils.getObjectMapper().convertValue(
                    write.value().get(column.getFieldCode()), Object.class));
        }
        for (String key : write.value().propertyNames()) {
            if (!known.contains(key)) throw new IllegalArgumentException("答案包含未定义字段: " + key);
        }
        return extension.isEmpty() ? null : JsonUtils.toJsonString(extension);
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("答案文本类型不正确");
        return value.asText();
    }
    private static Boolean bool(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw new IllegalArgumentException("答案布尔类型不正确");
        return value.booleanValue();
    }
    private static BigDecimal number(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw new IllegalArgumentException("答案数字类型不正确");
        return value.decimalValue();
    }
    private static void put(Map<String, Object> target, String key, Object value) { if (value != null) target.put(key, value); }
}
