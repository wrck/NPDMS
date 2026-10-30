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
 * 查询与取消使用 NPDMS 专用精确任务端点；取消受理不表示业务完成。
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
    @Autowired(required = false)
    private DacDesktopDispatchService desktop;

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
        Map<String, Object> body = null;
        try {
            validate(command);
            executionTimeoutSeconds(command);
            String idempotencyKey = command.platformTaskId();
            body = buildSubmission(command);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/api/v1/npdms/collections", HttpMethod.POST,
                    new HttpEntity<>(JsonUtils.toJsonString(body), dispatchHeaders(command)), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("DAC_SUBMIT_UNEXPECTED_STATUS_" + response.getStatusCode().value(),
                        new java.io.IOException("provider outcome unknown"));
            }
            DacCollectionSummary submission = JsonUtils.parseObjectQuietly(response.getBody(), DacCollectionSummary.class);
            if (submission == null || isBlank(submission.collectionId())) {
                throw new IllegalStateException("DAC_SUBMIT_RESPONSE_INCOMPLETE", new java.io.IOException("provider outcome unknown"));
            }
            try {
                recordDispatch(command, idempotencyKey, submission.collectionId(), "ACCEPTED");
            } catch (RuntimeException failure) {
                throw new IllegalStateException("DAC_DISPATCH_MAPPING_UNCONFIRMED", new java.io.IOException("mapping persistence failed"));
            }
            return new DeviceOpsDispatchResult(command.platformTaskId(), submission.collectionId(),
                    "ACCEPTED", true, Boolean.TRUE.equals(submission.existing()), command.traceId());
        } catch (org.springframework.web.client.RestClientResponseException failure) {
            if (failure.getStatusCode().is4xxClientError()) {
                return new DeviceOpsDispatchResult(command.platformTaskId(), null,
                        "HTTP_" + failure.getStatusCode().value(), false, false, command.traceId());
            }
            throw new IllegalStateException("DAC_DISPATCH_OUTCOME_UNKNOWN", new java.io.IOException("provider outcome unknown"));
        } catch (org.springframework.web.client.ResourceAccessException failure) {
            // Reconnect once using the same task identity. Never replay commands after an uncertain response.
            try {
                var recovered = findByExternalRequestId(command.platformTaskId(), namespace());
                if (recovered != null && !isBlank(recovered.collectionId())) {
                    recordDispatch(command, command.platformTaskId(), recovered.collectionId(), recovered.status());
                    return new DeviceOpsDispatchResult(command.platformTaskId(), recovered.collectionId(),
                            recovered.status(), true, true, command.traceId());
                }
            } catch (RuntimeException reconnectFailure) {
                log.warn("DAC reconnect deferred to reconciliation platformTaskId={} type={}",
                        command.platformTaskId(), reconnectFailure.getClass().getSimpleName());
            }
            throw new IllegalStateException("DAC_DISPATCH_OUTCOME_UNKNOWN", new java.io.IOException("provider transport unavailable"));
        } finally {
            if (body != null) erasePayloadSecret(body);
            if (command != null && command.temporarySecret() != null) Arrays.fill(command.temporarySecret(), '\0');
        }
    }

    @Override
    public DeviceOpsTaskSnapshot query(String platformTaskId) {
        if (isBlank(platformTaskId)) {
            throw new IllegalArgumentException("DAC 查询参数不完整");
        }
        String namespace = namespace();
        DeviceOpsDispatchDO dispatch = dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), platformTaskId);
        if (DacDesktopDispatchService.isDesktop(dispatch)) return desktop.query(dispatch);
        String collectionId = dispatch == null ? null : dispatch.getCollectionId();
        if (isBlank(collectionId)) {
            DacCollectionSummary summary = findByExternalRequestId(platformTaskId, namespace);
            if (summary != null && "CANCELLED_BEFORE_DISPATCH".equals(summary.status())) {
                return new DeviceOpsTaskSnapshot(platformTaskId, null, "CANCELLED", "CANCELLED_BEFORE_DISPATCH",
                        null, null, null, null);
            }
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
        DacCollectionSummary details = JsonUtils.parseObjectQuietly(response.getBody(), DacCollectionSummary.class);
        validateBinding(details, platformTaskId, namespace);
        if (!collectionId.equals(details.collectionId())) throw new IllegalStateException("DAC_COLLECTION_BINDING_MISMATCH");
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
        if (isBlank(platformTaskId) || isBlank(reason)) throw new IllegalArgumentException("DAC_CANCEL_INVALID");
        if (DacDesktopDispatchService.isDesktop(dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), platformTaskId))) {
            desktop.cancel(platformTaskId);
            return;
        }
        restTemplate.exchange(taskUri(platformTaskId, "/cancellations"), HttpMethod.POST,
                new HttpEntity<>(headers(null)), Void.class);
    }

    private DacCollectionSummary findByExternalRequestId(String platformTaskId, String namespace) {
        try {
            var response = restTemplate.exchange(taskUri(platformTaskId, ""), HttpMethod.GET,
                    new HttpEntity<>(headers(null)), String.class);
            DacCollectionSummary result = JsonUtils.parseObjectQuietly(response.getBody(), DacCollectionSummary.class);
            validateBinding(result, platformTaskId, namespace);
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound missing) {
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Gone cancelled) {
            if (cancelled.getResponseHeaders() == null || !"BEFORE_DISPATCH".equals(
                    cancelled.getResponseHeaders().getFirst("X-DAC-Cancellation"))) throw cancelled;
            return new DacCollectionSummary(null, namespace, null, platformTaskId, null,
                    "CANCELLED_BEFORE_DISPATCH", true);
        }
    }

    @Override
    public boolean retryResultDelivery(String platformTaskId) {
        if (DacDesktopDispatchService.isDesktop(dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), platformTaskId))) {
            desktop.redeliver(platformTaskId);
            return true;
        }
        restTemplate.exchange(taskUri(platformTaskId, "/result-redeliveries"), HttpMethod.POST,
                new HttpEntity<>(headers(null)), Void.class);
        return true;
    }

    @Override
    public List<Map<String, Object>> semanticResults(String platformTaskId) {
        if (isBlank(platformTaskId)) {
            throw new IllegalArgumentException("DAC 查询参数不完整");
        }
        if (DacDesktopDispatchService.isDesktop(dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), platformTaskId))) {
            throw new IllegalStateException("本机解析结果需要通过执行该任务的本机 DAC 读取");
        }
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(taskUri(platformTaskId, "/semantic-results"), HttpMethod.GET,
                    new HttpEntity<>(headers(null)), String.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound missing) {
            // 任务尚未下发或 DAC 无此发起对象：没有可解析的记录，返回空而非扩大为错误。
            return List.of();
        } catch (org.springframework.web.client.HttpClientErrorException.Gone cancelled) {
            // 下发前已取消的采集没有解析记录。
            return List.of();
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("DAC_SEMANTIC_RESULTS_UNEXPECTED_STATUS_" + response.getStatusCode().value());
        }
        List<Map<String, Object>> results =
                JsonUtils.parseObject(response.getBody(), new tools.jackson.core.type.TypeReference<>() {});
        return results == null ? List.of() : results;
    }

    private java.net.URI taskUri(String taskId, String suffix) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/npdms/collections/{id}" + suffix)
                .queryParam("namespace", namespace()).buildAndExpand(taskId).encode().toUri();
    }

    private String namespace() {
        return properties.getNamespace() + "-" + requiredTenantId();
    }

    private static void validateBinding(DacCollectionSummary result, String taskId, String namespace) {
        if (result == null || isBlank(result.collectionId()) || !namespace.equals(result.namespace())
                || !taskId.equals(result.externalRequestId()) || isBlank(result.status())) {
            throw new IllegalStateException("DAC_TASK_BINDING_MISMATCH");
        }
    }

    private Map<String, Object> buildSubmission(DeviceOpsDispatchCommand command) {
        return DacCollectionPayloadFactory.build(payloadInput(command));
    }

    private DacCollectionPayloadFactory.Input payloadInput(DeviceOpsDispatchCommand command) {
        return new DacCollectionPayloadFactory.Input(command, properties, namespace(), properties.getCallbackUrl());
    }

    private void recordDispatch(DeviceOpsDispatchCommand command, String idempotencyKey,
                                String collectionId, String externalStatus) {
        DeviceOpsDispatchDO dispatch = dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), command.platformTaskId());
        if (dispatch == null) {
            dispatch = new DeviceOpsDispatchDO();
            dispatch.setTenantId(command.tenantId());
            dispatch.setPlatformTaskId(command.platformTaskId());
            dispatch.setIdempotencyKey(idempotencyKey);
            dispatch.setNamespace(namespace());
            dispatch.setTraceId(command.traceId());
            try {
                dispatchMapper.insert(dispatch);
            } catch (org.springframework.dao.DuplicateKeyException concurrent) {
                dispatch = dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), command.platformTaskId());
                if (dispatch == null) throw concurrent;
            }
        }
        if (dispatch.getCollectionId() != null && !collectionId.equals(dispatch.getCollectionId())) {
            throw new IllegalStateException("DAC_COLLECTION_BINDING_CONFLICT");
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
        try {
            dispatchMapper.insert(dispatch);
        } catch (org.springframework.dao.DuplicateKeyException concurrent) {
            dispatch = dispatchMapper.selectByTenantAndPlatformTaskId(requiredTenantId(), platformTaskId);
            if (dispatch == null || !collectionId.equals(dispatch.getCollectionId())) {
                throw new IllegalStateException("DAC_COLLECTION_BINDING_CONFLICT");
            }
        }
        return dispatch;
    }

    private static Long requiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("DAC_GATEWAY_TENANT_REQUIRED");
        }
        return tenantId;
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

    private HttpHeaders dispatchHeaders(DeviceOpsDispatchCommand command) {
        String key = properties.getRequestSigningKey();
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("DAC_REQUEST_SIGNING_KEY_REQUIRED");
        }
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        String binding = java.util.stream.Stream.of(timestamp, namespace(), command.platformTaskId(), command.projectId(),
                command.deviceId(), command.host(), command.port().toString(), dacProtocol(command.protocol()),
                command.temporaryUsername(), "plt-" + command.templateId(), command.templateVersion(),
                command.templateHash().toLowerCase(java.util.Locale.ROOT),
                Long.toString(executionTimeoutSeconds(command)), properties.getCallbackUrl() == null ? "" : properties.getCallbackUrl())
                .map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining());
        if (command.savedConnectionId() != null) {
            String version = command.savedConnectionVersion().toString();
            binding += command.savedConnectionId().length() + ":" + command.savedConnectionId() + version.length() + ":" + version;
        }
        if(command.executionOptions()!=null)binding+=command.executionOptions().authorizationBinding();
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            HttpHeaders headers = headers(command.platformTaskId());
            headers.set("X-DAC-Grant-Time", timestamp);
            headers.set("X-DAC-Grant", HexFormat.of().formatHex(mac.doFinal(binding.getBytes(StandardCharsets.UTF_8))));
            return headers;
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("DAC_REQUEST_GRANT_FAILED");
        }
    }

    private long executionTimeoutSeconds(DeviceOpsDispatchCommand command) {
        return DacCollectionPayloadFactory.timeout(payloadInput(command));
    }

    private static void validate(DeviceOpsDispatchCommand command) {
        if (command == null || isBlank(command.platformTaskId()) || isBlank(command.host())
                || command.port() == null || isBlank(command.protocol()) || isBlank(command.templateId())
                || isBlank(command.templateVersion()) || command.commands() == null
                || isBlank(command.temporaryUsername()) || command.tenantId() == null || isBlank(command.projectId()) || isBlank(command.deviceId())) {
            throw new IllegalArgumentException("DAC 下发参数不完整");
        }
        boolean precheck = command.executionOptions()!=null && "PRECHECK".equals(command.executionOptions().purpose());
        if (precheck ? !command.commands().isEmpty() : command.commands().isEmpty()) {
            throw new IllegalArgumentException("采集用途与命令清单不一致");
        }
        boolean temporary = "TEMPORARY_SECRET".equals(command.credentialMode());
        boolean saved = "SAVED_CREDENTIAL".equals(command.credentialMode());
        if (!command.tenantId().equals(requiredTenantId()) || (!temporary && !saved)
                || (temporary && (command.temporarySecret() == null || command.temporarySecret().length == 0 || command.savedConnectionId() != null))
                || (saved && (isBlank(command.savedConnectionId()) || command.savedConnectionVersion() == null || command.temporarySecret() != null))) {
            throw new IllegalArgumentException("DAC_TASK_AUTHORIZATION_MISMATCH");
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

    record DacCollectionSummary(String collectionId, String namespace, String projectKey,
                                String externalRequestId, String activityType, String status,
                                Boolean existing) {
    }

    record DacCollectionPage(Long total, Integer page, Integer size, List<DacCollectionSummary> items) {
    }
}
