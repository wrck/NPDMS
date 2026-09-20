package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsDispatchDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsDispatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAC（Device Access & Collection）HTTP 生产网关。
 *
 * 下发契约见 docs/design/12-integration-design.md 第 11 节：以平台任务 ID 幂等，
 * 返回 DAC collectionId；下发超时先查询后重试；回调丢失通过状态查询补偿。
 * DAC V1 未提供集合级取消端点，{@link #cancel} 仅记录意图，不产生外部副作用。
 */
@Component
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
@Slf4j
public class DacDeviceOpsGateway implements DeviceOpsGatewayApi {

    /** NFR-02@V2：单命令阈值只允许 1~30 秒，下发契约不得携带超过 30 秒的阈值。 */
    static final long COMMAND_TIMEOUT_LIMIT_SECONDS = 30;

    private final DacDeviceOpsGatewayProperties properties;
    private final DeviceOpsDispatchMapper dispatchMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public DacDeviceOpsGateway(DacDeviceOpsGatewayProperties properties,
                               DeviceOpsDispatchMapper dispatchMapper) {
        this(properties, dispatchMapper, buildRestTemplate(properties));
    }

    DacDeviceOpsGateway(DacDeviceOpsGatewayProperties properties,
                        DeviceOpsDispatchMapper dispatchMapper,
                        RestTemplate restTemplate) {
        this.properties = properties;
        this.dispatchMapper = dispatchMapper;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate(DacDeviceOpsGatewayProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
        factory.setReadTimeout((int) properties.getReadTimeoutMillis());
        return new RestTemplate(factory);
    }

    @Override
    public DeviceOpsDispatchResult dispatch(DeviceOpsDispatchCommand command) {
        validate(command);
        String idempotencyKey = command.platformTaskId();
        Map<String, Object> body = buildSubmission(command);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/api/v1/collections", HttpMethod.POST,
                    new HttpEntity<>(JsonUtils.toJsonString(body), headers(idempotencyKey)), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("DAC_SUBMIT_UNEXPECTED_STATUS_" + response.getStatusCode().value());
            }
            DacCollectionSummary submission = JsonUtils.parseObject(response.getBody(), DacCollectionSummary.class);
            if (submission == null || isBlank(submission.collectionId())) {
                throw new IllegalStateException("DAC_SUBMIT_RESPONSE_INCOMPLETE");
            }
            recordDispatch(command, idempotencyKey, submission.collectionId(), "ACCEPTED");
            return new DeviceOpsDispatchResult(command.platformTaskId(), submission.collectionId(),
                    "ACCEPTED", true, Boolean.TRUE.equals(submission.existing()), command.traceId());
        } finally {
            erasePayloadSecret(body);
        }
    }

    @Override
    public DeviceOpsTaskSnapshot query(String platformTaskId) {
        if (isBlank(platformTaskId)) {
            throw new IllegalArgumentException("DAC 查询参数不完整");
        }
        String namespace = properties.getNamespace();
        DeviceOpsDispatchDO dispatch = dispatchMapper.selectByPlatformTaskId(platformTaskId);
        String collectionId = dispatch == null ? null : dispatch.getCollectionId();
        if (isBlank(collectionId)) {
            DacCollectionSummary summary = findByExternalRequestId(platformTaskId, namespace);
            if (summary == null) {
                return new DeviceOpsTaskSnapshot(platformTaskId, null, "UNKNOWN", "NOT_DISPATCHED",
                        null, null, null, null);
            }
            collectionId = summary.collectionId();
            if (dispatch == null) {
                dispatch = recordRecoveredDispatch(platformTaskId, namespace, collectionId, summary.status());
            }
        }
        ResponseEntity<String> response = restTemplate.exchange(
                properties.getBaseUrl() + "/api/v1/collections/" + collectionId + "?namespace=" + namespace,
                HttpMethod.GET, new HttpEntity<>(headers(null)), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("DAC_QUERY_UNEXPECTED_STATUS_" + response.getStatusCode().value());
        }
        DacCollectionSummary details = JsonUtils.parseObject(response.getBody(), DacCollectionSummary.class);
        if (details == null || isBlank(details.status())) {
            throw new IllegalStateException("DAC_QUERY_RESPONSE_INCOMPLETE");
        }
        if (dispatch == null) {
            recordRecoveredDispatch(platformTaskId, namespace, collectionId, details.status());
        } else if (!details.status().equals(dispatch.getExternalStatus())) {
            dispatch.setExternalStatus(details.status());
            dispatchMapper.updateById(dispatch);
        }
        return new DeviceOpsTaskSnapshot(platformTaskId, collectionId, details.status(), null,
                null, null, null, null);
    }

    @Override
    public void cancel(String platformTaskId, String reason) {
        // DAC V1 只有解析任务（parse-tasks）取消端点，未提供集合级取消；取消意图仅记录，等待 DAC 端点后接入。
        log.info("DAC collection cancel unsupported, platformTaskId={}, reason={}", platformTaskId, reason);
    }

    private DacCollectionSummary findByExternalRequestId(String platformTaskId, String namespace) {
        int page = 0;
        while (page < 10) {
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/api/v1/management/collections?namespace=" + namespace
                            + "&page=" + page + "&size=" + properties.getReconcilePageSize(),
                    HttpMethod.GET, new HttpEntity<>(headers(null)), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("DAC_RECONCILE_LIST_STATUS_" + response.getStatusCode().value());
            }
            DacCollectionPage listing = JsonUtils.parseObject(response.getBody(), DacCollectionPage.class);
            if (listing == null || listing.items() == null || listing.items().isEmpty()) {
                return null;
            }
            for (DacCollectionSummary item : listing.items()) {
                if (platformTaskId.equals(item.externalRequestId())) {
                    return item;
                }
            }
            if (listing.items().size() < properties.getReconcilePageSize()) {
                return null;
            }
            page++;
        }
        return null;
    }

    private Map<String, Object> buildSubmission(DeviceOpsDispatchCommand command) {
        boolean hasProject = !isBlank(command.projectId());
        if (hasCallbackUrl() && (!hasProject || isBlank(command.deviceId()))) {
            throw new IllegalArgumentException("回调地址需要项目与设备上下文");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        if (hasProject) {
            Map<String, Object> project = new LinkedHashMap<>();
            project.put("namespace", properties.getNamespace());
            project.put("projectKey", command.projectId());
            context.put("project", project);
        }
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceKey", command.deviceId());
        device.put("deviceName", command.deviceName());
        context.put("device", device);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("protocol", dacProtocol(command.protocol()));
        connection.put("host", command.host());
        connection.put("port", command.port());
        connection.put("username", command.temporaryUsername());
        connection.put("authenticationType", "PASSWORD");
        connection.put("executionMode", "EXEC");
        connection.put("password", command.temporarySecret().clone());
        connection.put("connectTimeoutSeconds",
                Math.max(1, properties.getDeviceConnectTimeoutSeconds()));

        String scriptContent = String.join("\n", command.commands());
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("source", "ADHOC_INLINE");
        script.put("key", "plt-" + command.templateId());
        script.put("version", command.templateVersion());
        script.put("content", scriptContent);
        script.put("sha256", sha256Hex(scriptContent));
        script.put("policy", "EXECUTION_ONLY");
        script.put("parserType", properties.getParserType());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", properties.getNamespace());
        body.put("context", context);
        body.put("connection", connection);
        body.put("script", script);
        body.put("externalRequestId", command.platformTaskId());
        body.put("activityType", properties.getActivityType());
        if (hasCallbackUrl()) {
            body.put("callbackUrl", properties.getCallbackUrl());
        }
        body.put("commandTimeoutSeconds",
                Math.min(properties.getCommandTimeoutSeconds(), COMMAND_TIMEOUT_LIMIT_SECONDS));
        body.put("parseTimeoutSeconds", Math.max(1, properties.getParseTimeoutSeconds()));
        body.put("leaseGraceSeconds", 0L);
        return body;
    }

    private void recordDispatch(DeviceOpsDispatchCommand command, String idempotencyKey,
                                String collectionId, String externalStatus) {
        DeviceOpsDispatchDO dispatch = dispatchMapper.selectByPlatformTaskId(command.platformTaskId());
        if (dispatch == null) {
            dispatch = new DeviceOpsDispatchDO();
            dispatch.setTenantId(command.tenantId());
            dispatch.setPlatformTaskId(command.platformTaskId());
            dispatch.setIdempotencyKey(idempotencyKey);
            dispatch.setNamespace(properties.getNamespace());
            dispatch.setTraceId(command.traceId());
            dispatchMapper.insert(dispatch);
        }
        dispatch.setCollectionId(collectionId);
        dispatch.setExternalStatus(externalStatus);
        dispatchMapper.updateById(dispatch);
    }

    private DeviceOpsDispatchDO recordRecoveredDispatch(String platformTaskId, String namespace,
                                                        String collectionId, String externalStatus) {
        DeviceOpsDispatchDO dispatch = new DeviceOpsDispatchDO();
        dispatch.setTenantId(requiredTenantId());
        dispatch.setPlatformTaskId(platformTaskId);
        dispatch.setIdempotencyKey(platformTaskId);
        dispatch.setNamespace(namespace);
        dispatch.setCollectionId(collectionId);
        dispatch.setExternalStatus(externalStatus);
        dispatchMapper.insert(dispatch);
        return dispatch;
    }

    private static Long requiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("DAC_GATEWAY_TENANT_REQUIRED");
        }
        return tenantId;
    }

    private boolean hasCallbackUrl() {
        return properties.getCallbackUrl() != null && !properties.getCallbackUrl().isBlank();
    }

    private HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
            headers.setBearerAuth(properties.getBearerToken());
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private static void validate(DeviceOpsDispatchCommand command) {
        if (command == null || isBlank(command.platformTaskId()) || isBlank(command.host())
                || command.port() == null || isBlank(command.protocol()) || isBlank(command.templateId())
                || isBlank(command.templateVersion()) || command.commands() == null || command.commands().isEmpty()
                || isBlank(command.temporaryUsername()) || command.temporarySecret() == null
                || command.temporarySecret().length == 0 || command.tenantId() == null) {
            throw new IllegalArgumentException("DAC 下发参数不完整");
        }
        if (command.commands().stream().anyMatch(DacDeviceOpsGateway::isBlank)) {
            throw new IllegalArgumentException("采集命令不能为空");
        }
    }

    private static String dacProtocol(String protocol) {
        return switch (protocol.toUpperCase()) {
            case "SSH", "SSH2" -> "SSH2";
            case "TELNET" -> "TELNET";
            default -> throw new IllegalArgumentException("不支持的采集协议: " + protocol);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void erasePayloadSecret(Map<String, Object> body) {
        Object connection = body.get("connection");
        if (connection instanceof Map<?, ?> connectionMap) {
            Object password = connectionMap.get("password");
            if (password instanceof char[] secret) {
                Arrays.fill(secret, '\0');
            }
        }
    }

    private static String sha256Hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    record DacCollectionSummary(String collectionId, String namespace, String projectKey,
                                String externalRequestId, String activityType, String status,
                                Boolean existing) {
    }

    record DacCollectionPage(Long total, Integer page, Integer size, List<DacCollectionSummary> items) {
    }
}
