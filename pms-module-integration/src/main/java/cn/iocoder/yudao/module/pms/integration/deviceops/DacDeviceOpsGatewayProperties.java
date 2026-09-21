package cn.iocoder.yudao.module.pms.integration.deviceops;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DAC（Device Access & Collection，设备连接与采集中心）外部端配置档案。
 * 数值超时/重试与生产端点由外部技术 Owner 在联调前登记，见 docs/design/12-integration-design.md 第 11、14 节。
 */
@Component
@ConfigurationProperties(prefix = "pms.integration.device-ops")
@Getter
@Setter
public class DacDeviceOpsGatewayProperties {

    /** 未开启时不装配 DAC 网关，平台派发 Bean 保持不激活。 */
    private boolean enabled = false;

    /** DAC 服务端点，本地联调实例为 http://127.0.0.1:48181。 */
    private String baseUrl = "http://127.0.0.1:48181";

    /** DAC 命名空间，须在 JWT device_ops_namespaces 授权范围内。 */
    private String namespace = "npdms";

    /** 下发集合的 activityType 标识。 */
    private String activityType = "PLATFORM_COLLECTION";

    /**
     * 单命令超时（秒）。NFR-02@V2 在线巡检单命令阈值只允许 1~30 秒，超过 30 秒必须拒绝。
     */
    private long commandTimeoutSeconds = 30;

    /** 配置调试手工执行的整组命令总时限；DAC 将此值用于整个脚本，而非每行命令。 */
    private long manualExecutionTimeoutSeconds = 600;

    /**
     * 采集结果解析超时（秒）。DAC 端 @Min(1) 必填，禁用语义解析时仍须合法。
     */
    private long parseTimeoutSeconds = 30;

    /**
     * DAC 到设备的 TCP 连接超时（秒）。DAC 端直连必填且 @Min(1)。
     */
    private long deviceConnectTimeoutSeconds = 10;

    /** 下发请求可选回调地址；当前 DAC 实例 callback.enabled=false 时留空，以对账轮询为恢复路径。 */
    private String callbackUrl;

    /** DAC 安全模式为 oauth2 时使用的 Bearer 令牌；local 模式留空。 */
    private String bearerToken;
    /** Task-bound, short-lived submission grants; injected from the deployment secret store. */
    private String requestSigningKey;

    /** 采集脚本内 parserType 标识（禁用语义解析时仅作来源标识）。 */
    private String parserType = "NONE";

    private long connectTimeoutMillis = 5000;

    private long readTimeoutMillis = 15000;

    /** 按 externalRequestId 兜底反查时单页拉取的管理列表条数。 */
    private int reconcilePageSize = 50;

    @jakarta.annotation.PostConstruct
    public void validateDeployment() {
        if (!enabled) return;
        requireEndpoint(baseUrl);
        requireEndpoint(callbackUrl);
        if (namespace == null || !namespace.matches("[a-zA-Z0-9_-]{1,64}")
                || requestSigningKey == null || requestSigningKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32
                || commandTimeoutSeconds < 1 || commandTimeoutSeconds > 30 || manualExecutionTimeoutSeconds < 1
                || connectTimeoutMillis < 1 || readTimeoutMillis < 1
                || deviceConnectTimeoutSeconds < 1 || parseTimeoutSeconds < 1) {
            throw new IllegalArgumentException("DAC_CONFIGURATION_INVALID");
        }
    }

    private static void requireEndpoint(String value) {
        try {
            var uri = java.net.URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && java.util.List.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("DAC_ENDPOINT_REQUIRES_HTTPS");
        }
    }
}
