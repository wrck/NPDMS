package cn.iocoder.yudao.module.pms.integration.deviceops;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

/** Short-lived handshake only; never contains credentials, task tickets or private keys. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DacDesktopConnectStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> REPLACE = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end "
            + "local ttl=redis.call('PTTL',KEYS[1]); if ttl <= 0 then return 0 end "
            + "redis.call('PSETEX',KEYS[1],ttl,ARGV[2]); return 1", Long.class);
    private static String key(String id) { return "pms:dac:connect:" + id; }
    public boolean create(String id, String value) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(id), value, Duration.ofSeconds(90)));
    }
    public String get(String id) { return redis.opsForValue().get(key(id)); }
    public boolean replace(Change change) {
        return Long.valueOf(1).equals(redis.execute(REPLACE, List.of(key(change.id())), change.before(), change.after()));
    }
    public record Change(String id, String before, String after) { }
}
