package cn.iocoder.yudao.module.pms.cutover.service.spare.port;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CUT对INT-06备件系统集成ACL的消费端口；不提供生产fallback。 */
public interface SpareApplicationGateway {

    SpareInitiationProviderResult initiate(SpareInitiationCommand command);

    SpareStatusProviderResult queryStatus(SpareStatusQuery query);

    record SpareInitiationCommand(Long tenantId, String platformRequestId, Long taskId, String taskNo,
                                  Integer taskVersion, Long projectId, List<SpareDeviceContext> devices,
                                  SpareNeedSnapshot need, String correlationId) {
        public SpareInitiationCommand {
            positive(tenantId, "tenantId");
            text(platformRequestId, 128, "platformRequestId");
            positive(taskId, "taskId");
            text(taskNo, 64, "taskNo");
            nonNegative(taskVersion, "taskVersion");
            positive(projectId, "projectId");
            require(devices != null && !devices.isEmpty(), "devices");
            devices = devices.stream().sorted(Comparator.comparing(SpareDeviceContext::deviceId)).toList();
            for (int index = 1; index < devices.size(); index++) {
                require(!devices.get(index - 1).deviceId().equals(devices.get(index).deviceId()), "devices");
            }
            require(need != null && need.required(), "need");
            text(correlationId, 128, "correlationId");
        }
    }

    record SpareDeviceContext(Long deviceId, String serialNumber, Long projectAssignmentVersion) {
        public SpareDeviceContext {
            positive(deviceId, "deviceId");
            text(serialNumber, 128, "serialNumber");
            nonNegative(projectAssignmentVersion, "projectAssignmentVersion");
        }
    }

    record SpareInitiationProviderResult(String externalSystemCode, String externalRequestId,
                                         String externalApplicationNo, String launchUrl,
                                         SpareProviderStatusFact initialStatus) {
        public SpareInitiationProviderResult {
            text(externalSystemCode, 64, "externalSystemCode");
            text(externalRequestId, 128, "externalRequestId");
            optionalText(externalApplicationNo, 128, "externalApplicationNo");
            optionalText(launchUrl, 2048, "launchUrl");
            require(externalApplicationNo != null || launchUrl != null, "provider result");
            if (launchUrl != null) require(launchUrl.startsWith("https://"), "launchUrl");
        }
    }

    record SpareStatusQuery(Long tenantId, String platformRequestId, String externalSystemCode,
                            String externalRequestId, String externalApplicationNo,
                            Long expectedStatusVersion, String correlationId) {
        public SpareStatusQuery {
            positive(tenantId, "tenantId");
            text(platformRequestId, 128, "platformRequestId");
            text(externalSystemCode, 64, "externalSystemCode");
            text(externalRequestId, 128, "externalRequestId");
            text(externalApplicationNo, 128, "externalApplicationNo");
            if (expectedStatusVersion != null) positive(expectedStatusVersion, "expectedStatusVersion");
            text(correlationId, 128, "correlationId");
        }
    }

    record SpareStatusProviderResult(String externalSystemCode, String externalRequestId,
                                     String externalApplicationNo, SpareProviderStatusFact status) {
        public SpareStatusProviderResult {
            text(externalSystemCode, 64, "externalSystemCode");
            text(externalRequestId, 128, "externalRequestId");
            text(externalApplicationNo, 128, "externalApplicationNo");
            require(status != null, "status");
        }
    }

    record SpareProviderStatusFact(Long statusVersion, String externalStatusRaw,
                                   Map<String, Object> statusSnapshot, LocalDateTime externalOccurredAt,
                                   LocalDateTime observedAt) {
        public SpareProviderStatusFact {
            positive(statusVersion, "statusVersion");
            text(externalStatusRaw, 128, "externalStatusRaw");
            statusSnapshot = jsonObject(statusSnapshot, "statusSnapshot");
            require(observedAt != null, "observedAt");
        }
    }

    private static void positive(Long value, String field) {
        require(value != null && value > 0, field);
    }

    private static void nonNegative(Number value, String field) {
        require(value != null && value.longValue() >= 0, field);
    }

    private static void text(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= max, field);
    }

    private static void optionalText(String value, int max, String field) {
        if (value != null) text(value, max, field);
    }

    private static Map<String, Object> jsonObject(Map<String, Object> value, String field) {
        require(value != null, field);
        String json;
        try {
            json = JsonUtils.toJsonString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid " + field, exception);
        }
        require(json.getBytes(StandardCharsets.UTF_8).length <= 16 * 1024, field);
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static void require(boolean condition, String field) {
        if (!condition) throw new IllegalArgumentException("invalid " + field);
    }
}
