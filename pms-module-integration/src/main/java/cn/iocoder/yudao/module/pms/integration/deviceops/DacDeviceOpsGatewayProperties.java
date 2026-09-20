package cn.iocoder.yudao.module.pms.integration.deviceops;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DAC（Device Access & Collection，设备连接与采集中心）外部端配置档案。
 * 数值超时/重试与生产端点由外部技术 Owner 在联调前登记，见 docs/design/12-integration-design.md 第 11、14 节。
 */
@Component
@ConfigurationProperties(prefix = "pms.integration.device-ops")
@Data
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

    /** 采集脚本内 parserType 标识（禁用语义解析时仅作来源标识）。 */
    private String parserType = "NONE";

    private long connectTimeoutMillis = 5000;

    private long readTimeoutMillis = 15000;

    /** 按 externalRequestId 兜底反查时单页拉取的管理列表条数。 */
    private int reconcilePageSize = 50;
}
