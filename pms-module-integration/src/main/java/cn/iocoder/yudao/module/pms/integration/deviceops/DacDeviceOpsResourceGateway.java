package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import tools.jackson.databind.JsonNode;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;

@Component
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
public class DacDeviceOpsResourceGateway implements DeviceOpsResourceApi {
    private final DacDeviceOpsGatewayProperties properties;
    private final RestTemplate http;
    public DacDeviceOpsResourceGateway(DacDeviceOpsGatewayProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
        factory.setReadTimeout((int) Math.max(properties.getReadTimeoutMillis(), 15000));
        this.http = new RestTemplate(factory);
    }
    @Override public Connection saveConnection(ConnectionCommand command) {
        try {
            var connection = new LinkedHashMap<String, Object>();
            connection.put("host", command.host()); connection.put("port", command.port());
            connection.put("protocol", "SSH".equals(command.protocol()) ? "SSH2" : "TELNET");
            connection.put("username", command.username()); connection.put("password", command.secret());
            connection.put("authenticationType", "PASSWORD"); connection.put("executionMode", "SHELL");
            connection.put("connectTimeoutSeconds", properties.getDeviceConnectTimeoutSeconds());
            if ("TELNET".equals(command.protocol())) connection.put("telnetPrompts", Map.of(
                    "login", "(?i)(login|username)\\s*:\\s*$", "password", "(?i)password\\s*:\\s*$", "command", "[>#\\$]\\s*$", "lineEnding", "AUTO"));
            JsonNode result = JsonUtils.parseTree(http.exchange(uri("/api/v1/npdms/resources/connections/{id}", command.id(), false), HttpMethod.PUT,
                    new HttpEntity<>(JsonUtils.toJsonString(Map.of("namespace", namespace(), "displayName", command.name(), "connection", connection)), headers()), String.class).getBody());
            if (result == null || !result.path("saved").asBoolean()) {
                String code=result==null?"":result.path("test").path("errorCode").asText();
                String reason=switch(code) {
                    case "AUTH_FAILED","AUTHENTICATION_FAILED" -> "设备认证失败，请核对用户名和密码";
                    case "CONNECT_TIMEOUT" -> "连接设备超时，请核对网络与端口";
                    case "PROMPT_NOT_FOUND" -> "未识别设备登录提示符";
                    case "UNREACHABLE" -> "设备不可达，请核对地址和端口";
                    case "PROTOCOL_DISABLED" -> "设备连接协议未启用";
                    case "HOST_KEY_MISMATCH" -> "设备主机密钥不匹配";
                    default -> "设备连接验证失败";
                };
                throw new ResourceException(reason+"，凭证未保存");
            }
            return metadata(result.path("connection"), command.id());
        } catch (org.springframework.web.client.RestClientException failure) {
            throw new ResourceException("DAC 保存连接响应未确认，请使用原请求重试");
        } finally { if (command.secret() != null) Arrays.fill(command.secret(), '\0'); }
    }
    @Override public Connection getConnection(String id) {
        try {
            return metadata(JsonUtils.parseTree(http.exchange(uri("/api/v1/saved-connections/{id}", id, true), HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class).getBody()), id);
        } catch (org.springframework.web.client.RestClientException failure) {
            throw new ResourceException("DAC 保存连接暂不可用，请刷新后重试");
        }
    }
    @Override public void registerScript(String key, String version, String content, String sha256) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl()).path("/api/v1/npdms/resources/scripts/{key}/versions/{version}")
                .buildAndExpand(key, version).encode().toUri();
        try {
            http.exchange(uri, HttpMethod.PUT, new HttpEntity<>(JsonUtils.toJsonString(Map.of("namespace", namespace(), "content", content, "sha256", sha256)), headers()), Void.class);
        } catch (org.springframework.web.client.RestClientException failure) {
            throw new ResourceException("DAC 模板登记未确认，内容已冻结，可重试发布同一版本");
        }
    }
    private Connection metadata(JsonNode result, String expectedId) {
        if (result == null || !expectedId.equals(result.path("id").asText()) || !namespace().equals(result.path("namespace").asText())
                || !result.path("credentialSaved").asBoolean() || !result.path("version").canConvertToLong()) throw new IllegalStateException("DAC_CONNECTION_BINDING_MISMATCH");
        JsonNode c = result.path("connection");
        String protocol = switch(c.path("protocol").asText()) { case "SSH2" -> "SSH"; case "TELNET" -> "TELNET"; default -> throw new IllegalStateException("DAC_CONNECTION_PROTOCOL_INVALID"); };
        if (!"PASSWORD".equals(c.path("authenticationType").asText()) || !"SHELL".equals(c.path("executionMode").asText())) throw new IllegalStateException("DAC_CONNECTION_AUTH_INVALID");
        return new Connection(expectedId, c.path("host").asText(), c.path("port").asInt(), protocol, c.path("username").asText(), result.path("version").asLong());
    }
    private String namespace() { return properties.getNamespace() + "-" + TenantContextHolder.getRequiredTenantId(); }
    private URI uri(String path, String id, boolean query) {
        var builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl()).path(path);
        if (query) builder.queryParam("namespace", namespace());
        return builder.buildAndExpand(id).encode().toUri();
    }
    private HttpHeaders headers() {
        var headers = new HttpHeaders(); headers.setBearerAuth(properties.getBearerToken()); headers.setContentType(MediaType.APPLICATION_JSON); return headers;
    }
}
