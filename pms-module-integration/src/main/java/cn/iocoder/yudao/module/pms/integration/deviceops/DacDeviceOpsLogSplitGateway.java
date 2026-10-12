package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsLogSplitApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.URI;
import java.util.*;

@Component
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DacDeviceOpsLogSplitGateway implements DeviceOpsLogSplitApi {
    private final DacDeviceOpsGatewayProperties properties;
    private final RestTemplate http;
    public DacDeviceOpsLogSplitGateway(DacDeviceOpsGatewayProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeoutMillis());
        factory.setReadTimeout((int) properties.getReadTimeoutMillis());
        this.http = new RestTemplate(factory);
    }
    @Override public Result split(Command command) {
        if (command == null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.projectId() == null || command.projectId() <= 0 || command.content() == null)
            throw new IllegalArgumentException("日志拆分范围无效");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(properties.getBearerToken());
        try {
            String body = JsonUtils.toJsonString(Map.of("namespace", properties.getNamespace()+"-"+command.tenantId(),
                    "projectId", command.projectId().toString(), "format", command.format(), "content", command.content()));
            var result = JsonUtils.parseObject(http.postForObject(URI.create(properties.getBaseUrl()+"/api/v1/npdms/log-splits"),
                    new HttpEntity<>(body, headers), String.class), Result.class);
            if (result == null || result.devices().isEmpty() || result.sanitizedSource() == null
                    || result.redactedValueCount() < 0) throw new IllegalStateException("DAC 日志拆分响应无效");
            return result;
        } catch (HttpClientErrorException failure) {
            if (failure.getStatusCode().value() == 422) {
                // Read only whitelisted failure metadata, never propagate arbitrary remote body text.
                String reason = "日志块缺少唯一设备序列号、内容不完整或格式不受支持，请核对后重新上传";
                try {
                    var rejection = JsonUtils.parseTree(failure.getResponseBodyAsString());
                    reason = switch (rejection.path("code").asText()) {
                        case "DEVICE_SERIAL_MISSING" -> "日志块缺少设备序列号";
                        case "DEVICE_SERIAL_AMBIGUOUS" -> "日志块包含多个设备序列号，无法唯一关联";
                        case "COLLECTION_LOG_INCOMPLETE" -> "采集日志内容不完整，未归档为有效设备日志";
                        case "LOG_SIZE_INVALID" -> "日志文件大小无效";
                        case "LOG_FORMAT_UNSUPPORTED", "COLLECTION_LOG_FORMAT_INVALID", "COLLECTION_BLOCKS_MISSING" -> "日志文件格式不受支持";
                        default -> reason;
                    };
                    if (rejection.path("block").canConvertToInt() && rejection.path("block").asInt()>0)
                        reason += "（第 "+rejection.path("block").asInt()+" 块）";
                } catch (RuntimeException ignored) { /* Keep the safe fixed message. */ }
                throw new Rejected(reason);
            }
            if (failure.getStatusCode().value()==401 || failure.getStatusCode().value()==403)
                throw new IllegalStateException("DAC 日志拆分授权失败");
            if (failure.getStatusCode().value()==404)
                throw new IllegalStateException("当前 DAC 未提供日志拆分接口，请部署对应版本");
            throw new IllegalStateException("DAC 未接受日志拆分请求");
        } catch (RestClientException failure) {
            throw new IllegalStateException("DAC 日志拆分暂不可用，请重试");
        }
    }
}
