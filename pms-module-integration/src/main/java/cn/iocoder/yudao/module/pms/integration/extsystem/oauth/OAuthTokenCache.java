package cn.iocoder.yudao.module.pms.integration.extsystem.oauth;

import java.util.function.Supplier;

/**
 * 分布式 OAuth2 Token 缓存接口。
 *
 * <p>为 D365 / FP / OA 等外部系统提供统一的 OAuth2 access token 缓存能力，
 * 替代各 {@code IntegrationServiceImpl} 中原有的进程内（{@code ConcurrentHashMap}）
 * token 缓存。基于 Redis 实现分布式缓存与互斥刷新，确保多实例部署下：</p>
 *
 * <ul>
 *   <li><b>缓存共享</b>：所有应用实例共享同一份 token，避免各实例独立请求导致
 *       token 端点被频繁调用（部分 OAuth2 服务器对 token 请求有速率限制）。</li>
 *   <li><b>单飞防击穿</b>：token 失效后仅一个实例 / 线程执行刷新，其余通过
 *       {@link TokenRefreshLock} 等待结果，避免缓存击穿引发 token 端点雪崩。</li>
 *   <li><b>提前续期</b>：在 token 实际过期前 5 分钟即触发刷新，避免边缘过期
 *       导致业务请求携带已失效 token。</li>
 *   <li><b>失败告警</b>：连续获取失败达到阈值（默认 3 次）时记录 ERROR 日志并
 *       递增业务指标 {@code pms_oauth_failure_total{system=...}}，供告警系统采集。</li>
 * </ul>
 *
 * <p>典型用法（以 D365 为例）：</p>
 * <pre>{@code
 * @Service
 * public class D365IntegrationServiceImpl implements D365IntegrationService {
 *     private final OAuthTokenCache oauthTokenCache;
 *
 *     public String getAccessToken() {
 *         return oauthTokenCache.getToken("d365", this::fetchD365Token);
 *     }
 *
 *     private OAuthTokenCache.TokenInfo fetchD365Token() {
 *         TokenResponse response = requestToken();  // 调用 D365 OAuth2 token 端点
 *         long now = System.currentTimeMillis() / 1000;
 *         int expiresIn = response.getExpiresIn() == null ? 3600 : response.getExpiresIn();
 *         return OAuthTokenCache.TokenInfo.builder()
 *                 .accessToken(response.getAccessToken())
 *                 .expiresAt(now + expiresIn)
 *                 .tokenType(response.getTokenType())
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * @see RedisOAuthTokenCache
 * @see TokenRefreshLock
 */
public interface OAuthTokenCache {

    /**
     * 获取 token：命中缓存且未临近过期时直接返回，否则加锁刷新后返回新 token。
     *
     * <p>刷新采用「单飞」模式：通过 {@link TokenRefreshLock} 保证同一系统同一时刻
     * 只有一个线程调用 {@code tokenSupplier} 获取新 token，其余线程轮询缓存等待结果。
     * 若等待超时（默认 5 秒），抛出 {@link cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException}。</p>
     *
     * <p>当 {@code tokenSupplier} 抛出异常时，缓存内部会递增连续失败计数器：
     * <ul>
     *   <li>失败计数 &lt; 3：记录 WARN 日志</li>
     *   <li>失败计数 ≥ 3：记录 ERROR 日志并递增 Micrometer Counter
     *       {@code pms_oauth_failure_total{system=systemName}}</li>
     *   <li>成功获取后：失败计数器归零</li>
     * </ul>
     * 原始异常会被重新抛出，由调用方（Resilience4j 熔断器 / 调用方业务逻辑）处理。</p>
     *
     * @param systemName    系统标识（{@code d365} / {@code fp} / {@code oa}），作为缓存键的一部分
     * @param tokenSupplier 获取新 token 的函数；仅在缓存未命中或即将过期且获得锁时被调用
     * @return OAuth2 access token 字符串
     * @throws cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException 当 token 获取失败或等待刷新超时时
     */
    String getToken(String systemName, Supplier<TokenInfo> tokenSupplier);

    /**
     * 主动失效指定系统的 token 缓存。
     *
     * <p>当业务侧检测到 token 已被服务端撤销（如收到 401 Unauthorized）时调用，
     * 删除缓存中的 token，使下次 {@link #getToken} 强制重新获取。</p>
     *
     * @param systemName 系统标识
     */
    void invalidate(String systemName);

    /**
     * 获取指定系统的连续 token 获取失败次数（用于健康检查 / 告警展示）。
     *
     * <p>失败计数基于 Redis 存储，跨实例一致。成功获取 token 后自动归零。</p>
     *
     * @param systemName 系统标识
     * @return 当前连续失败次数，0 表示无失败或上次获取成功
     */
    int getFailureCount(String systemName);

    /**
     * Token 信息载体，由 {@code tokenSupplier} 返回，包含 token 字符串、过期时间与类型。
     *
     * <p>{@code expiresAt} 为 Unix 时间戳（秒），缓存据此计算 TTL 与提前刷新时机。
     * 建议由 token 端点返回的 {@code expires_in}（秒）加上当前时间戳计算得出。</p>
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class TokenInfo {
        /** OAuth2 access token 字符串。 */
        private String accessToken;

        /** Token 过期时间（Unix 时间戳，秒）。 */
        private long expiresAt;

        /** Token 类型（如 {@code Bearer}），可为 {@code null}。 */
        private String tokenType;
    }
}
