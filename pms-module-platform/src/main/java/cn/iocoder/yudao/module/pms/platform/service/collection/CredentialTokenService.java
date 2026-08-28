package cn.iocoder.yudao.module.pms.platform.service.collection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CredentialTokenService {

    private final Clock clock;
    private final TokenStore store;

    public CredentialTokenService(Clock clock, TokenStore store) {
        this.clock = Objects.requireNonNull(clock);
        this.store = Objects.requireNonNull(store);
    }

    public IssuedToken issue(TokenBinding binding, String username, char[] secret, Duration lifetime) {
        validateBinding(binding);
        if (username == null || username.isBlank() || secret == null || secret.length == 0
                || lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("取密令牌参数不完整");
        }
        Instant issuedAt = clock.instant();
        String jti = UUID.randomUUID().toString();
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        char[] storedSecret = Arrays.copyOf(secret, secret.length);
        try {
            store.put(new TokenRecord(hashToken(token), jti, binding, username,
                    storedSecret, issuedAt, issuedAt.plus(lifetime), TokenStatus.ACTIVE));
        } catch (RuntimeException ex) {
            Arrays.fill(storedSecret, '\0');
            throw ex;
        }
        return new IssuedToken(token, jti, issuedAt.plus(lifetime));
    }

    public TransientCredential consume(String token, TokenBinding expectedBinding) {
        validateBinding(expectedBinding);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("取密令牌不能为空");
        }
        TokenRecord record = store.consume(hashToken(token), expectedBinding, clock.instant());
        return new TransientCredential(record.username(), record.secret());
    }

    public void revoke(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti不能为空");
        }
        store.revoke(jti);
    }

    TokenStore store() {
        return store;
    }

    private static void validateBinding(TokenBinding binding) {
        if (binding == null || blank(binding.platformTaskId()) || blank(binding.deviceId())
                || blank(binding.protocol()) || blank(binding.templateId())
                || blank(binding.templateVersion()) || blank(binding.audience())) {
            throw new IllegalArgumentException("取密令牌绑定不完整");
        }
    }

    static String hashToken(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record TokenBinding(
            String platformTaskId,
            String deviceId,
            String protocol,
            String templateId,
            String templateVersion,
            String audience) {

        public String canonicalValue() {
            return String.join("\n", platformTaskId, deviceId, protocol, templateId, templateVersion, audience);
        }

        public static TokenBinding fromCanonicalValue(String value) {
            String[] parts = value.split("\\n", -1);
            if (parts.length != 6) {
                throw new IllegalStateException("CREDENTIAL_TOKEN_BINDING_INVALID");
            }
            return new TokenBinding(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
        }
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    public static final class TransientCredential implements AutoCloseable {

        private final String username;
        private final char[] secret;

        private TransientCredential(String username, char[] secret) {
            this.username = username;
            this.secret = secret;
        }

        public String username() {
            return username;
        }

        public char[] secret() {
            return secret;
        }

        public void clear() {
            Arrays.fill(secret, '\0');
        }

        @Override
        public void close() {
            clear();
        }
    }

    public interface TokenStore {

        void put(TokenRecord record);

        TokenRecord consume(String tokenHash, TokenBinding expectedBinding, Instant consumedAt);

        void revoke(String jti);
    }

    public static final class InMemoryTokenStore implements TokenStore {

        private final Map<String, TokenRecord> records = new ConcurrentHashMap<>();

        @Override
        public void put(TokenRecord record) {
            if (records.putIfAbsent(record.tokenHash(), record) != null) {
                throw new IllegalStateException("CREDENTIAL_TOKEN_COLLISION");
            }
        }

        @Override
        public synchronized TokenRecord consume(String tokenHash, TokenBinding expectedBinding, Instant consumedAt) {
            TokenRecord record = records.get(tokenHash);
            if (record == null) {
                throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_FOUND");
            }
            if (!record.binding().equals(expectedBinding)) {
                throw new IllegalStateException("CREDENTIAL_TOKEN_BINDING_MISMATCH");
            }
            if (record.status() != TokenStatus.ACTIVE) {
                throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_ACTIVE");
            }
            if (!consumedAt.isBefore(record.expiresAt())) {
                records.put(tokenHash, record.withStatus(TokenStatus.EXPIRED));
                record.clearSecret();
                throw new IllegalStateException("CREDENTIAL_TOKEN_EXPIRED");
            }
            records.put(tokenHash, record.withStatus(TokenStatus.CONSUMED));
            char[] secret = Arrays.copyOf(record.secret(), record.secret().length);
            record.clearSecret();
            return record.withSecret(secret);
        }

        @Override
        public synchronized void revoke(String jti) {
            for (Map.Entry<String, TokenRecord> entry : records.entrySet()) {
                TokenRecord record = entry.getValue();
                if (record.jti().equals(jti)) {
                    record.clearSecret();
                    entry.setValue(record.withStatus(TokenStatus.REVOKED));
                    return;
                }
            }
            throw new IllegalStateException("CREDENTIAL_TOKEN_NOT_FOUND");
        }
    }

    public record TokenRecord(
            String tokenHash,
            String jti,
            TokenBinding binding,
            String username,
            char[] secret,
            Instant issuedAt,
            Instant expiresAt,
            TokenStatus status) {

        TokenRecord withStatus(TokenStatus nextStatus) {
            return new TokenRecord(tokenHash, jti, binding, username, secret, issuedAt, expiresAt, nextStatus);
        }

        TokenRecord withSecret(char[] nextSecret) {
            return new TokenRecord(tokenHash, jti, binding, username, nextSecret, issuedAt, expiresAt, status);
        }

        void clearSecret() {
            Arrays.fill(secret, '\0');
        }
    }

    public enum TokenStatus {
        ACTIVE,
        CONSUMED,
        REVOKED,
        EXPIRED
    }
}
