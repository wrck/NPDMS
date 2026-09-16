package cn.iocoder.yudao.module.pms.integration.extsystem.oauth;

import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 基于 Redis Hash 的分布式 OAuth2 token 缓存实现。
 *
 * <p>每个系统的 token 存储为独立的 Redis Hash：</p>
 * <pre>
 * Key:   oauth:token:{systemName}
 * Field: accessToken  → token 字符串
 * Field: expiresAt     → Unix 时间戳（秒）
 * Field: tokenType     → token 类型（Bearer）
 * TTL:   expiresAt - now + 60s（安全裕量，确保 Redis 键先于 token 失效被清理）
 * </pre>
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li><b>缓存读取</b>：从 Redis Hash 反序列化为 {@link OAuthTokenCache.TokenInfo}，
 *       若未命中或即将过期（距过期不足 5 分钟）则进入刷新流程。</li>
 *   <li><b>单飞加锁</b>：通过 {@link TokenRefreshLock#tryLock} 尝试获取刷新锁。
 *     <ul>
 *       <li>成功 → 双重检查缓存（避免锁等待期间已被其他线程刷新）→ 调用
 *           {@code tokenSupplier} 获取新 token → 写入缓存 → 返回。</li>
 *       <li>失败 → 轮询缓存（每 200ms 一次，最多 5 秒），等待持锁线程刷新完成。</li>
 *     </ul>
 *   </li>
 *   <li><b>失败计数</b>：{@code tokenSupplier} 抛异常时递增 Redis 失败计数器，
 *       连续 ≥3 次记录 ERROR 日志并递增 Micrometer Counter
 *       {@code pms_oauth_failure_total{system=...}}。成功后计数器归零。</li>
 * </ol>
 *
 * <h3>提前续期</h3>
 * <p>{@code REFRESH_AHEAD_SECONDS = 300}（5 分钟）：当 token 距过期不足 5 分钟时
 * 即触发刷新，避免 token 在业务请求传输过程中过期。各系统可根据端点响应时间
 * 调整此裕量。</p>
 *
 * <h3>线程安全</h3>
 * <p>所有操作均通过 Redis 原子命令完成，无共享可变状态。{@link #failureCounters}
 * 使用 {@link ConcurrentHashMap} 缓存 Micrometer Counter 实例（Counter 本身线程安全），
 * 避免每次失败都查找 / 注册 Counter。</p>
 *
 * @see OAuthTokenCache
 * @see TokenRefreshLock
 */
@Slf4j
@Component
public class RedisOAuthTokenCache implements OAuthTokenCache {

    /** Redis Hash 缓存键前缀：{@code oauth:token:{systemName}}。 */
    private static final String CACHE_KEY_PREFIX = "oauth:token:";

    /** 连续失败计数器键前缀：{@code oauth:failcount:{systemName}}。 */
    private static final String FAILCOUNT_KEY_PREFIX = "oauth:failcount:";

    /** Hash field: access token。 */
    private static final String FIELD_ACCESS_TOKEN = "accessToken";

    /** Hash field: 过期时间戳（秒）。 */
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    /** Hash field: token 类型。 */
    private static final String FIELD_TOKEN_TYPE = "tokenType";

    /** 提前刷新时间（秒）：距过期不足 5 分钟即触发刷新。 */
    private static final long REFRESH_AHEAD_SECONDS = 300;

    /** 连续失败告警阈值：达到 3 次记录 ERROR + 递增指标。 */
    private static final int FAILURE_THRESHOLD = 3;

    /** 轮询等待间隔（毫秒）。 */
    private static final long WAIT_INTERVAL_MILLIS = 200;

    /** 轮询等待最大时长（毫秒）：超时抛异常。 */
    private static final long MAX_WAIT_MILLIS = 5000;

    /** 失败计数器 TTL（秒）：5 分钟内无失败则自动归零。 */
    private static final long FAILCOUNT_TTL_SECONDS = 300;

    /** Micrometer 业务指标名：OAuth2 token 获取失败总数。 */
    private static final String METRIC_NAME = "pms_oauth_failure_total";

    private final StringRedisTemplate redisTemplate;
    private final TokenRefreshLock refreshLock;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** Micrometer Counter 缓存（按系统名），避免重复注册同名 meter。 */
    private final ConcurrentHashMap<String, Counter> failureCounters = new ConcurrentHashMap<>();

    /**
     * 构造器注入。
     *
     * <p>{@link MeterRegistry} 通过 {@link ObjectProvider} 注入，运行时由
     * pms-admin 的 spring-boot-starter-actuator + micrometer-registry-prometheus
     * 提供。若运行环境未配置 actuator（如独立单元测试），{@code getIfAvailable()}
     * 返回 {@code null}，指标记录被跳过，不影响缓存核心功能。</p>
     *
     * @param redisTemplate        Redis 字符串模板
     * @param refreshLock          token 刷新互斥锁
     * @param meterRegistryProvider Micrometer 指标注册中心（可选）
     */
    public RedisOAuthTokenCache(StringRedisTemplate redisTemplate,
                                 TokenRefreshLock refreshLock,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.redisTemplate = redisTemplate;
        this.refreshLock = refreshLock;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public String getToken(String systemName, Supplier<TokenInfo> tokenSupplier) {
        String cacheKey = CACHE_KEY_PREFIX + systemName;

        // 1. 尝试从缓存读取（无锁快速路径）
        TokenInfo cached = readFromCache(cacheKey);
        if (cached != null && !isExpiringSoon(cached)) {
            return cached.getAccessToken();
        }

        // 2. 缓存未命中或即将过期 → 加锁刷新
        boolean locked = false;
        try {
            locked = refreshLock.tryLock(systemName);
            if (locked) {
                // 双重检查：锁等待期间可能已被其他线程刷新
                cached = readFromCache(cacheKey);
                if (cached != null && !isExpiringSoon(cached)) {
                    return cached.getAccessToken();
                }
                // 获取新 token（持锁独占）
                TokenInfo fresh;
                try {
                    fresh = tokenSupplier.get();
                } catch (Exception e) {
                    // 记录失败计数 + 指标，重新抛出原始异常
                    recordFailure(systemName, e);
                    throw e;
                }
                if (fresh == null || fresh.getAccessToken() == null) {
                    IntegrationException ex = new IntegrationException(systemName,
                            systemName + " token response is empty");
                    recordFailure(systemName, ex);
                    throw ex;
                }
                writeToCache(cacheKey, fresh);
                resetFailureCount(systemName);
                log.debug("OAuth token 刷新成功: system={}", systemName);
                return fresh.getAccessToken();
            } else {
                // 未获得锁 → 轮询等待持锁线程刷新完成
                return waitForFreshToken(cacheKey, systemName);
            }
        } finally {
            if (locked) {
                refreshLock.unlock(systemName);
            }
        }
    }

    @Override
    public void invalidate(String systemName) {
        String cacheKey = CACHE_KEY_PREFIX + systemName;
        try {
            redisTemplate.delete(cacheKey);
            log.info("OAuth token 缓存已主动失效: system={}", systemName);
        } catch (Exception e) {
            log.warn("OAuth token 缓存失效操作异常: system={}, err={}", systemName, e.getMessage());
        }
    }

    @Override
    public int getFailureCount(String systemName) {
        String key = FAILCOUNT_KEY_PREFIX + systemName;
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) {
                return 0;
            }
            return Integer.parseInt(val);
        } catch (Exception e) {
            log.debug("读取 OAuth 失败计数异常: system={}, err={}", systemName, e.getMessage());
            return 0;
        }
    }

    // ---- 内部方法 ----

    /**
     * 从 Redis Hash 读取并反序列化 token 信息。
     *
     * @param cacheKey Redis Hash 键
     * @return token 信息；缓存不存在或解析失败返回 {@code null}
     */
    private TokenInfo readFromCache(String cacheKey) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            Object tokenObj = entries.get(FIELD_ACCESS_TOKEN);
            if (tokenObj == null) {
                return null;
            }
            String accessToken = tokenObj.toString();
            Object expiresObj = entries.get(FIELD_EXPIRES_AT);
            if (expiresObj == null) {
                return null;
            }
            long expiresAt;
            try {
                expiresAt = Long.parseLong(expiresObj.toString());
            } catch (NumberFormatException e) {
                log.warn("缓存中的 expiresAt 解析失败: key={}, val={}", cacheKey, expiresObj);
                return null;
            }
            Object typeObj = entries.get(FIELD_TOKEN_TYPE);
            return TokenInfo.builder()
                    .accessToken(accessToken)
                    .expiresAt(expiresAt)
                    .tokenType(typeObj == null ? null : typeObj.toString())
                    .build();
        } catch (Exception e) {
            log.warn("从 Redis 读取 OAuth token 缓存异常: key={}, err={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /**
     * 将 token 信息写入 Redis Hash 并设置 TTL。
     *
     * <p>TTL = {@code expiresAt - now + 60s}，确保 Redis 键先于 token 实际过期被清理，
     * 60 秒安全裕量覆盖时钟偏差。若计算出的 TTL ≤ 0（token 已过期），设为 60 秒兜底。</p>
     *
     * @param cacheKey  Redis Hash 键
     * @param tokenInfo token 信息
     */
    private void writeToCache(String cacheKey, TokenInfo tokenInfo) {
        long now = System.currentTimeMillis() / 1000;
        long ttl = tokenInfo.getExpiresAt() - now + 60;
        if (ttl <= 0) {
            ttl = 60;
        }
        Map<String, String> entries = new HashMap<>(3);
        entries.put(FIELD_ACCESS_TOKEN, tokenInfo.getAccessToken());
        entries.put(FIELD_EXPIRES_AT, String.valueOf(tokenInfo.getExpiresAt()));
        if (tokenInfo.getTokenType() != null) {
            entries.put(FIELD_TOKEN_TYPE, tokenInfo.getTokenType());
        }
        try {
            redisTemplate.opsForHash().putAll(cacheKey, entries);
            redisTemplate.expire(cacheKey, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            // Redis 写入失败不阻断 token 返回（调用方已拿到 token），仅记录日志
            log.error("写入 OAuth token 缓存失败（token 仍返回但不缓存）: key={}, err={}",
                    cacheKey, e.getMessage());
        }
    }

    /**
     * 判断 token 是否即将过期（距过期不足 {@link #REFRESH_AHEAD_SECONDS} 秒）。
     *
     * @param tokenInfo token 信息
     * @return {@code true} 即将过期，需要刷新
     */
    private boolean isExpiringSoon(TokenInfo tokenInfo) {
        long now = System.currentTimeMillis() / 1000;
        return tokenInfo.getExpiresAt() - now < REFRESH_AHEAD_SECONDS;
    }

    /**
     * 轮询等待持锁线程刷新完成。
     *
     * <p>每 {@link #WAIT_INTERVAL_MILLIS} 毫秒读取一次缓存，若发现有效 token 则返回；
     * 超过 {@link #MAX_WAIT_MILLIS} 毫秒仍未刷新完成则抛出异常。</p>
     *
     * @param cacheKey   Redis Hash 键
     * @param systemName 系统标识（用于异常消息）
     * @return 刷新后的 token
     * @throws IntegrationException 等待超时
     */
    private String waitForFreshToken(String cacheKey, String systemName) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IntegrationException(systemName,
                        "等待 " + systemName + " token 刷新被中断");
            }
            TokenInfo cached = readFromCache(cacheKey);
            if (cached != null && !isExpiringSoon(cached)) {
                return cached.getAccessToken();
            }
        }
        throw new IntegrationException(systemName,
                "等待 " + systemName + " token 刷新超时（" + MAX_WAIT_MILLIS + "ms）");
    }

    /**
     * 记录 token 获取失败：递增 Redis 连续失败计数 + Micrometer 指标，达到阈值记录 ERROR。
     *
     * @param systemName 系统标识
     * @param e          触发失败的异常
     */
    private void recordFailure(String systemName, Exception e) {
        String key = FAILCOUNT_KEY_PREFIX + systemName;
        int failures = 1;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null) {
                failures = count.intValue();
            }
            // 每次失败刷新 TTL，确保 5 分钟内无失败则计数器自动归零
            redisTemplate.expire(key, Duration.ofSeconds(FAILCOUNT_TTL_SECONDS));
        } catch (Exception redisEx) {
            log.warn("记录 OAuth 失败计数异常（Redis 不可用）: system={}, err={}",
                    systemName, redisEx.getMessage());
        }

        // 递增 Micrometer Counter（每次失败都记录，用于失败率监控）
        incrementFailureMetric(systemName);

        if (failures >= FAILURE_THRESHOLD) {
            log.error("OAuth token 获取连续失败 {} 次（达到告警阈值 {}）: system={}, lastError={}",
                    failures, FAILURE_THRESHOLD, systemName, e.getMessage());
        } else {
            log.warn("OAuth token 获取失败 {}/{}: system={}, err={}",
                    failures, FAILURE_THRESHOLD, systemName, e.getMessage());
        }
    }

    /**
     * 成功获取 token 后重置失败计数器。
     *
     * @param systemName 系统标识
     */
    private void resetFailureCount(String systemName) {
        try {
            redisTemplate.delete(FAILCOUNT_KEY_PREFIX + systemName);
        } catch (Exception e) {
            log.debug("重置 OAuth 失败计数异常: system={}, err={}", systemName, e.getMessage());
        }
    }

    /**
     * 递增 Micrometer Counter {@code pms_oauth_failure_total{system=...}}。
     *
     * <p>若运行环境未配置 {@link MeterRegistry}（如独立单元测试），跳过指标记录。</p>
     *
     * @param systemName 系统标识（作为 tag 值）
     */
    private void incrementFailureMetric(String systemName) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        failureCounters.computeIfAbsent(systemName, s -> Counter.builder(METRIC_NAME)
                        .description("OAuth2 token 获取失败总数")
                        .tag("system", s)
                        .register(registry))
                .increment();
    }
}
