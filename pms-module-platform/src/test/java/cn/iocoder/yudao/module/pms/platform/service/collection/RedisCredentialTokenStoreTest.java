package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class RedisCredentialTokenStoreTest {

    private static final String KEY_PREFIX = "it:credential-token:";

    private StringRedisTemplate redisTemplate;
    private CredentialTokenService service;

    @BeforeEach
    void setUp() {
        org.springframework.data.redis.connection.RedisStandaloneConfiguration configuration =
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration("127.0.0.1", redisPort());
        configuration.setPassword(repositoryValue("NPDMS_REDIS_PASSWORD"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
        service = new CredentialTokenService(
                Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
                new RedisCredentialTokenStore(redisTemplate, KEY_PREFIX, new TestSecretProtector()));
    }

    @Test
    void storesProtectedSecretInsteadOfRecoverableEncoding() {
        CredentialTokenService.TokenBinding binding = new CredentialTokenService.TokenBinding(
                "task-protected", "device-1", "SSH", "template-1", "v1", "DEVICE_OPS");
        char[] secret = "secret-value".toCharArray();

        CredentialTokenService.IssuedToken token = service.issue(
                binding, "operator", secret, Duration.ofMinutes(2));

        String tokenHash = CredentialTokenService.hashToken(token.token());
        Object stored = redisTemplate.opsForHash().get(KEY_PREFIX + "token:" + tokenHash, "secret");
        assertNotEquals("secret-value", stored);
        assertNotEquals(Base64.getEncoder().encodeToString("secret-value".getBytes(java.nio.charset.StandardCharsets.UTF_8)), stored);
        assertFalse(String.valueOf(stored).contains("secret-value"));
        try (CredentialTokenService.TransientCredential credential = service.consume(token.token(), binding)) {
            assertEquals("secret-value", new String(credential.secret()));
        }
    }

    @Test
    void concurrentConsumptionHasExactlyOneWinner() throws Exception {
        CredentialTokenService.TokenBinding binding = new CredentialTokenService.TokenBinding(
                "task-redis", "device-1", "SSH", "template-1", "v1", "DEVICE_OPS");
        CredentialTokenService.IssuedToken token = service.issue(
                binding, "operator", "secret".toCharArray(), Duration.ofMinutes(2));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> consume(start, token.token(), binding));
            Future<Boolean> second = executor.submit(() -> consume(start, token.token(), binding));
            start.countDown();

            long winners = java.util.stream.Stream.of(first.get(), second.get()).filter(Boolean::booleanValue).count();
            assertEquals(1L, winners);
        }
    }

    private boolean consume(CountDownLatch start, String token,
                            CredentialTokenService.TokenBinding binding) throws Exception {
        start.await();
        try (CredentialTokenService.TransientCredential ignored = service.consume(token, binding)) {
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private static int redisPort() {
        String value = repositoryValue("NPDMS_REDIS_PORT");
        return value.isBlank() ? 16379 : Integer.parseInt(value);
    }

    private static String repositoryValue(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        for (java.nio.file.Path directory = java.nio.file.Path.of("").toAbsolutePath().normalize();
                directory != null; directory = directory.getParent()) {
            java.nio.file.Path dotenv = directory.resolve(".env");
            if (!java.nio.file.Files.isRegularFile(dotenv)) {
                continue;
            }
            try {
                for (String line : java.nio.file.Files.readAllLines(dotenv)) {
                    if (line.startsWith(key + "=")) {
                        return line.substring(line.indexOf('=') + 1).trim();
                    }
                }
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("无法读取当前仓库.env", ex);
            }
        }
        return "";
    }

    private static final class TestSecretProtector implements CredentialSecretProtector {

        @Override
        public String protect(char[] secret) {
            return "protected:" + new StringBuilder(new String(secret)).reverse();
        }

        @Override
        public char[] reveal(String protectedSecret) {
            return new StringBuilder(protectedSecret.substring("protected:".length())).reverse().toString().toCharArray();
        }
    }
}
