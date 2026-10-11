package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
public class DacDeviceLogParsingGateway implements DeviceLogParsingApi {
    private final DacDeviceOpsGatewayProperties properties;
    private final RestTemplate http;

    public DacDeviceLogParsingGateway(DacDeviceOpsGatewayProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
        factory.setReadTimeout((int) Math.max(properties.getReadTimeoutMillis(), 15000));
        http = new RestTemplate(factory);
    }

    @Override
    public ParseTask submit(Submission command) {
        var body = new SubmitBody(namespace(), command.projectKey(), command.requestId(),
                command.sourceCollectionId(), command.inputText(), command.commandHint());
        try {
            String response = http.exchange(properties.getBaseUrl() + "/api/v1/log-parses",
                    HttpMethod.POST, new HttpEntity<>(JsonUtils.toJsonString(body), headers()), String.class).getBody();
            return parse(response, command.requestId(), command.projectKey(), command.sourceCollectionId());
        } catch (HttpStatusCodeException error) {
            throw failure(error);
        } catch (RestClientException error) {
            throw new ParsingException("解析提交响应未确认，请先刷新状态；如未受理，可使用原日志和原请求重试");
        }
    }

    @Override
    public ParseTask find(String requestId, String projectKey, String sourceCollectionId) {
        var uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/log-parses/{id}").queryParam("namespace", namespace())
                .buildAndExpand(requestId).encode().toUri();
        try {
            String response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), String.class).getBody();
            return parse(response, requestId, projectKey, sourceCollectionId);
        } catch (HttpStatusCodeException error) {
            if (error.getStatusCode().value() == 404 && "TASK_NOT_FOUND".equals(errorCode(error))) return null;
            throw failure(error);
        } catch (RestClientException error) {
            throw new ParsingException("DAC 解析状态暂不可用，请稍后刷新；不会重新连接设备或下发命令");
        }
    }

    private ParseTask parse(String raw, String requestId, String project, String sourceTask) {
        Response result = JsonUtils.parseObjectQuietly(raw, Response.class);
        if (result == null || !requestId.equals(result.requestId()) || !namespace().equals(result.namespace())
                || !project.equals(result.projectKey()) || !Objects.equals(sourceTask, result.sourceCollectionId())
                || result.taskId() == null || result.taskId().isBlank() || result.state() == null
                || !Set.of("QUEUED", "RUNNING", "WAITING", "SUCCEEDED", "FAILED", "CANCELLED").contains(result.state())
                || ("SUCCEEDED".equals(result.state()) && result.result() == null)
                || (result.result() != null && (!result.taskId().equals(result.result().get("taskId"))
                    || !(result.result().get("semanticResult") instanceof Map)))) {
            throw new ParsingException("DAC 解析结果绑定校验失败");
        }
        return new ParseTask(result.taskId(), result.state(), result.waitReason(), result.releaseVersion(),
                result.warnings() == null ? List.of() : List.copyOf(result.warnings()), result.result());
    }

    private ParsingException failure(HttpStatusCodeException error) {
        String code = errorCode(error);
        String message = switch (code) {
            case "ACTIVE_RELEASE_NOT_FOUND", "RELEASE_NOT_PUBLISHED" -> "DAC 尚无可用的已发布解析版本，请启用解析版本后重试";
            case "INPUT_TOO_LARGE" -> "日志超过 DAC 解析大小上限";
            case "INVALID_REQUEST", "INVALID_INPUT" -> "日志格式无效或原采集日志尚不可解析，请检查输入";
            case "IDEMPOTENCY_CONFLICT" -> "原解析请求内容已变化，请新建解析任务";
            default -> "DAC 未受理日志解析，请检查服务、解析版本和访问权限后重试";
        };
        return new ParsingException(message);
    }

    private String errorCode(HttpStatusCodeException error) {
        var body = JsonUtils.parseObjectQuietly(error.getResponseBodyAsString(), ErrorBody.class);
        return body == null || body.code() == null ? "" : body.code();
    }

    private String namespace() { return properties.getNamespace() + "-" + TenantContextHolder.getRequiredTenantId(); }
    private HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(properties.getBearerToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private record SubmitBody(String namespace, String projectKey, String requestId, String sourceCollectionId,
                              String inputText, String commandHint) { }
    private record ErrorBody(String code) { }
    private record Response(String requestId, String namespace, String projectKey, String sourceCollectionId,
                            String taskId, String state, String waitReason, String releaseVersion,
                            List<String> warnings, Map<String, Object> result) { }
}
