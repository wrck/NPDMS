package cn.iocoder.yudao.module.pms.integration.extsystem.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis SETNX 的分布式互斥锁，用于 OAuth2 token 刷新单飞控制。
 *
 * <p>当多个线程 / 实例同时发现 token 缓存未命中或即将过期时，通过本锁保证
 * 仅一个线程执行 token 刷新（调用 token 端点），其余线程等待结果写入缓存。
 * 避免缓存击穿导致 token 端点被并发请求打满。</p>
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li><b>加锁</b>：{@code SET key value NX EX ttl}（{@link StringRedisTemplate#opsForValue()
 *       .setIfAbsent}），原子性地完成「不存在则写入 + 设置 TTL」。
 *       {@code value} 为 UUID，作为锁持有者标识。</li>
 *   <li><b>TTL 防死锁</b>：默认 30 秒 TTL，即使持有者进程崩溃未释放，锁也会自动过期，
 *       避免其他线程永久阻塞。token 刷新通常在数秒内完成，30 秒裕量充足。</li>
 *   <li><b>解锁</b>：通过 Lua 脚本 {@code if get(key)==value then del(key) end} 原子性
 *       释放，确保只删除自己持有的锁（防止误删已被其他线程重新获取的锁）。</li>
 *   <li><b>ThreadLocal 持有者</b>：加锁时将 UUID 存入 {@link ThreadLocal}，
 *       解锁时取出用于 Lua 脚本比对；{@code finally} 中必须清理，防止线程池复用导致泄漏。</li>
 * </ul>
 *
 * <p><b>非可重入</b>：本锁不支持同线程重入。若同一线程在持有锁期间再次调用
 * {@link #tryLock}，将返回 {@code false}。OAuth2 token 刷新流程不会递归调用，
 * 故无需可重入。</p>
 *
 * <p><b>tryLock 立即返回</b>：不阻塞等待，获取失败直接返回 {@code false}，
 * 由调用方（{@link RedisOAuthTokenCache}）决定后续策略（轮询等待缓存）。</p>
 *
 * @see RedisOAuthTokenCache
 */
@Slf4j
@Component
public class TokenRefreshLock {

    /** Redis 锁键前缀。 */
    private static final String LOCK_KEY_PREFIX = "oauth:lock:";

    /** 锁 TTL（秒）：防止持有者进程崩溃导致死锁。 */
    private static final long LOCK_TTL_SECONDS = 30;

    /**
     * 解锁 Lua 脚本：仅当 Redis 中存储的值与本线程持有的 UUID 一致时才删除（释放锁）。
     *
     * <p>使用 Lua 保证「读取 + 比对 + 删除」三步原子执行，避免以下竞态：</p>
     * <pre>
     * 线程 A: GET key → 返回 "uuid-A"（锁即将过期）
     *         —— 此时锁过期，线程 B SETNX 成功获取锁（值为 "uuid-B"）
     * 线程 A: DEL key  → 误删了线程 B 的锁！
     * </pre>
     * <p>Lua 脚本在 Redis 服务端单线程执行，避免上述竞态。</p>
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 保存当前锁实例在本线程持有的锁信息。
     *
     * <p>不能声明为 {@code static}：多个 {@link TokenRefreshLock} 实例（尤其是测试上下文
     * 重建时）共享同一个 ThreadLocal 会把上一实例的持锁状态泄漏到下一实例。</p>
     */
    private final ThreadLocal<LockOwnership> lockHolder = new ThreadLocal<>();

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造器注入。
     *
     * @param redisTemplate Redis 字符串模板（由 pms-common 的 spring-boot-starter-data-redis 提供）
     */
    public TokenRefreshLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取指定系统的 token 刷新锁（立即返回，不阻塞）。
     *
     * <p>使用 {@code SET key value NX EX 30} 原子操作：键不存在则写入并设置 TTL，
     * 返回 {@code true}；键已存在则不写入，返回 {@code false}。</p>
     *
     * <p>加锁成功后，UUID 会存入 {@link ThreadLocal}，供 {@link #unlock} 比对使用。
     * 调用方必须在 {@code finally} 块中调用 {@link #unlock} 释放锁并清理 ThreadLocal。</p>
     *
     * @param systemName 系统标识（{@code d365} / {@code fp} / {@code oa}）
     * @return {@code true} 获取锁成功；{@code false} 锁已被其他线程持有
     */
    public boolean tryLock(String systemName) {
        String lockKey = LOCK_KEY_PREFIX + systemName;
        if (lockHolder.get() != null) {
            // 非可重入锁：同一实例、同一线程已持有任意系统的刷新锁时直接失败。
            return false;
        }
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (Boolean.TRUE.equals(acquired)) {
            lockHolder.set(new LockOwnership(lockKey, lockValue));
            return true;
        }
        return false;
    }

    /**
     * 释放指定系统的 token 刷新锁。
     *
     * <p>通过 Lua 脚本原子性地「比对 UUID + 删除」：仅当 Redis 中的值与本线程
     * ThreadLocal 中存储的 UUID 一致时才删除，避免误删其他线程的锁。</p>
     *
     * <p>若当前线程未持有锁（{@link #tryLock} 返回 {@code false} 或已解锁），
     * 本方法为空操作。<b>ThreadLocal 始终在 finally 中清理</b>，防止线程池复用泄漏。</p>
     *
     * @param systemName 系统标识
     */
    public void unlock(String systemName) {
        String lockKey = LOCK_KEY_PREFIX + systemName;
        LockOwnership ownership = lockHolder.get();
        if (ownership == null) {
            // 当前线程未持有锁（tryLock 返回 false 或已解锁），直接返回
            return;
        }
        if (!ownership.lockKey().equals(lockKey)) {
            // 调用方传入了其他系统名，不释放也不清理真正持有的锁。
            log.warn("忽略不匹配的 OAuth token 刷新锁释放请求: requested={}, held={}",
                    lockKey, ownership.lockKey());
            return;
        }
        try {
            Long result = redisTemplate.execute(
                    UNLOCK_SCRIPT, List.of(lockKey), ownership.lockValue());
            if (result != null && result == 0) {
                // 锁已过期被其他线程获取，或已被自动释放，无需处理
                log.debug("OAuth 刷新锁已非本线程持有，跳过删除: system={}", systemName);
            }
        } catch (Exception e) {
            // Redis 异常不阻断主流程（锁有 TTL 会自动过期），仅记录日志
            log.warn("释放 OAuth token 刷新锁失败（锁将自动过期）: system={}, err={}",
                    systemName, e.getMessage());
        } finally {
            lockHolder.remove();
        }
    }

    /** 当前线程持有的 Redis 锁键及其唯一持有者值。 */
    private record LockOwnership(String lockKey, String lockValue) {
    }
}
