package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RedisCredentialTokenStore implements CredentialTokenService.TokenStore {

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local state = redis.call('HGET', KEYS[1], 'status')
            if not state then return 'NOT_FOUND' end
            if state ~= 'ACTIVE' then return 'NOT_ACTIVE' end
            local binding = redis.call('HGET', KEYS[1], 'binding')
            if binding ~= ARGV[1] then return 'BINDING_MISMATCH' end
            local expiresAt = tonumber(redis.call('HGET', KEYS[1], 'expiresAt'))
            if expiresAt <= tonumber(ARGV[2]) then
              redis.call('HSET', KEYS[1], 'status', 'EXPIRED', 'secret', '')
              return 'EXPIRED'
            end
            local username = redis.call('HGET', KEYS[1], 'username')
            local secret = redis.call('HGET', KEYS[1], 'secret')
            local jti = redis.call('HGET', KEYS[1], 'jti')
            local issuedAt = redis.call('HGET', KEYS[1], 'issuedAt')
            redis.call('HSET', KEYS[1], 'status', 'CONSUMED', 'secret', '')
            return table.concat({'OK', username, secret, jti, issuedAt, tostring(expiresAt)}, string.char(10))
            """, String.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local tokenKey = redis.call('GET', KEYS[1])
            if not tokenKey then return 0 end
            if redis.call('EXISTS', tokenKey) == 0 then return 0 end
            redis.call('HSET', tokenKey, 'status', 'REVOKED', 'secret', '')
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final CredentialSecretProtector secretProtector;

    public RedisCredentialTokenStore(StringRedisTemplate redisTemplate, String keyPrefix,
                                     CredentialSecretProtector secretProtector) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.secretProtector = secretProtector;
    }

    @Override
    public void put(CredentialTokenService.TokenRecord record) {
        String tokenKey = tokenKey(record.tokenHash());
        String protectedSecret;
        try {
            protectedSecret = secretProtector.protect(record.secret());
        } finally {
            record.clearSecret();
        }
        Boolean created = redisTemplate.opsForHash().putIfAbsent(tokenKey, "status", record.status().name());
        if (!Boolean.TRUE.equals(created)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_COLLISION");
        }
        redisTemplate.opsForHash().putAll(tokenKey, java.util.Map.of(
                "jti", record.jti(),
                "binding", record.binding().canonicalValue(),
                "username", record.username(),
                "secret", protectedSecret,
                "issuedAt", String.valueOf(record.issuedAt().toEpochMilli()),
                "expiresAt", String.valueOf(record.expiresAt().toEpochMilli())));
        Duration ttl = Duration.between(Instant.now(), record.expiresAt()).plusMinutes(5);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(5);
        }
        redisTemplate.expire(tokenKey, ttl);
        redisTemplate.opsForValue().set(jtiKey(record.jti()), tokenKey, ttl);
    }

    @Override
    public CredentialTokenService.TokenRecord consume(String tokenHash,
                                                       CredentialTokenService.TokenBinding expectedBinding,
                                                       Instant consumedAt) {
        String result = redisTemplate.execute(CONSUME_SCRIPT, List.of(tokenKey(tokenHash)),
                expectedBinding.canonicalValue(), String.valueOf(consumedAt.toEpochMilli()));
        if (result == null || "NOT_FOUND".equals(result)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_FOUND");
        }
        if ("NOT_ACTIVE".equals(result)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_ACTIVE");
        }
        if ("BINDING_MISMATCH".equals(result)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_BINDING_MISMATCH");
        }
        if ("EXPIRED".equals(result)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_EXPIRED");
        }
        String[] fields = result.split("\n", -1);
        if (fields.length != 6 || !"OK".equals(fields[0])) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_STORE_RESPONSE_INVALID");
        }
        return new CredentialTokenService.TokenRecord(tokenHash, fields[3], expectedBinding, fields[1],
                secretProtector.reveal(fields[2]), Instant.ofEpochMilli(Long.parseLong(fields[4])),
                Instant.ofEpochMilli(Long.parseLong(fields[5])), CredentialTokenService.TokenStatus.CONSUMED);
    }

    @Override
    public void revoke(String jti) {
        Long revoked = redisTemplate.execute(REVOKE_SCRIPT, List.of(jtiKey(jti)));
        if (!Long.valueOf(1L).equals(revoked)) {
            throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_FOUND");
        }
    }

    private String tokenKey(String tokenHash) {
        return keyPrefix + "token:" + tokenHash;
    }

    private String jtiKey(String jti) {
        return keyPrefix + "jti:" + jti;
    }

}
