package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverTaskReqVO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.util.MultiValueMap;

import java.util.HashSet;
import java.util.Set;

/** F-CUT-002局部严格请求解码；不改变Yudao全局ObjectMapper。 */
public final class CutoverTaskRequestCodec {

    private static final Set<String> RESOLVE_KEYS = Set.of("serialNumbers");
    private static final Set<String> CREATE_KEYS = Set.of(
            "projectId", "configurationCode", "serialNumbers", "taskName", "background", "cutoverType",
            "networkMode", "scheduledTime", "expectedProjectContext", "expectedProjectScopeVersion",
            "expectedDeviceScopeWatermark", "expectedReadinessSnapshotId", "expectedReadinessSnapshotVersion",
            "expectedCustomerServiceLevelStatus", "expectedCustomerServiceLevelRevisionId",
            "expectedCustomerServiceLevelCode", "expectedCustomerServiceLevelFactVersion",
            "expectedCustomerServiceLevelEffectiveFrom", "expectedCustomerServiceLevelEffectiveTo");
    private static final Set<String> PROJECT_KEYS = Set.of(
            "projectId", "projectVersion", "projectCode", "projectName", "customerId", "customerCode",
            "customerName", "officeDepartmentId", "officeCode", "officeName");
    private static final Set<String> DEVICE_KEYS = Set.of("deviceId", "serialNumber", "projectAssignmentVersion");
    private static final Set<String> ASSESSMENT_KEYS = Set.of("answers", "manualGrade");
    private static final Set<String> ANSWER_KEYS = Set.of(
            "businessImportanceLevel", "operationComplexityLevel", "hiddenRiskLevel", "sparePartApplied");
    private static final Set<String> LIST_QUERY_KEYS = Set.of(
            "projectId", "taskStatus", "currentStage", "pageNo", "pageSize");
    private static final Set<String> TASK_STATUSES = Set.of(
            "GRADE_CONFIRMING", "SURVEYING", "PLAN_DRAFTING", "LEGACY_UNKNOWN");
    private static final Set<String> TASK_STAGES = Set.of("P2", "P3", "P4");

    private final ObjectMapper objectMapper;

    public CutoverTaskRequestCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CutoverTaskReqVO.ResolveCreateContext resolveCreateContext(JsonNode body) {
        exact(body, RESOLVE_KEYS, "resolve-create-context");
        return read(body, CutoverTaskReqVO.ResolveCreateContext.class);
    }

    public CutoverTaskReqVO.Create create(JsonNode body) {
        exact(body, CREATE_KEYS, "create");
        exact(body.get("expectedProjectContext"), PROJECT_KEYS, "expectedProjectContext");
        JsonNode devices = body.get("expectedDeviceScopeWatermark");
        require(devices != null && devices.isArray() && !devices.isEmpty(), "expectedDeviceScopeWatermark");
        devices.forEach(device -> exact(device, DEVICE_KEYS, "deviceWatermarkEntry"));
        return read(body, CutoverTaskReqVO.Create.class);
    }

    public CutoverTaskReqVO.SaveAssessment saveAssessment(JsonNode body) {
        exact(body, ASSESSMENT_KEYS, "save-assessment");
        exact(body.get("answers"), ANSWER_KEYS, "answers");
        return read(body, CutoverTaskReqVO.SaveAssessment.class);
    }

    public ListQuery listQuery(MultiValueMap<String, String> query) {
        require(query != null && LIST_QUERY_KEYS.containsAll(query.keySet())
                && query.values().stream().allMatch(values -> values != null && values.size() == 1), "query keys");
        Long projectId = optionalLong(query.getFirst("projectId"), "projectId");
        require(projectId == null || projectId > 0, "projectId");
        String taskStatus = optionalEnum(query.getFirst("taskStatus"), TASK_STATUSES, "taskStatus");
        String currentStage = optionalEnum(query.getFirst("currentStage"), TASK_STAGES, "currentStage");
        int pageNo = optionalInt(query.getFirst("pageNo"), 1, "pageNo");
        int pageSize = optionalInt(query.getFirst("pageSize"), 20, "pageSize");
        require(pageNo > 0 && pageSize > 0 && pageSize <= 100, "page");
        return new ListQuery(projectId, taskStatus, currentStage, pageNo, pageSize);
    }

    public void emptyCommand(JsonNode body) {
        exact(body, Set.of(), "empty-command");
    }

    private <T> T read(JsonNode body, Class<T> type) {
        try {
            return objectMapper.treeToValue(body, type);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid request body", exception);
        }
    }

    private static void exact(JsonNode node, Set<String> expected, String type) {
        require(node != null && node.isObject(), type);
        Set<String> actual = new HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        require(actual.equals(expected), type + " keys");
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }

    private static Long optionalLong(String value, String field) {
        if (value == null) return null;
        require(!value.isBlank() && value.equals(value.trim()), field);
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid " + field, exception);
        }
    }

    private static int optionalInt(String value, int defaultValue, String field) {
        Long parsed = optionalLong(value, field);
        require(parsed == null || parsed <= Integer.MAX_VALUE, field);
        return parsed == null ? defaultValue : parsed.intValue();
    }

    private static String optionalEnum(String value, Set<String> allowed, String field) {
        if (value == null) return null;
        require(allowed.contains(value), field);
        return value;
    }

    public record ListQuery(Long projectId, String taskStatus, String currentStage, int pageNo, int pageSize) {
    }
}
