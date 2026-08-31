package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverTaskReqVO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
}
