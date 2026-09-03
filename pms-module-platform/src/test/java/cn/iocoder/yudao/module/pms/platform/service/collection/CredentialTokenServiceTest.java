package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T01:00:00Z");

    @Test
    void tokenIsBoundAndCanOnlyBeConsumedOnce() {
        CredentialTokenService service = serviceAt(NOW);
        char[] secret = "secret-value".toCharArray();
        CredentialTokenService.TokenBinding binding = binding("task-1");
        CredentialTokenService.IssuedToken token = service.issue(binding, "user", secret, Duration.ofMinutes(2));

        CredentialTokenService.TransientCredential credential = service.consume(token.token(), binding);

        assertEquals("user", credential.username());
        assertArrayEquals("secret-value".toCharArray(), credential.secret());
        assertThrows(IllegalStateException.class, () -> service.consume(token.token(), binding));
        credential.clear();
        assertArrayEquals(new char[12], credential.secret());
    }

    @Test
    void mismatchedBindingIsRejectedWithoutConsumingToken() {
        CredentialTokenService service = serviceAt(NOW);
        CredentialTokenService.TokenBinding binding = binding("task-1");
        CredentialTokenService.IssuedToken token = service.issue(
                binding, "user", "secret".toCharArray(), Duration.ofMinutes(2));

        assertThrows(IllegalStateException.class,
                () -> service.consume(token.token(), binding("task-2")));
        assertEquals("user", service.consume(token.token(), binding).username());
    }

    @Test
    void revokedOrExpiredTokenCannotBeConsumed() {
        CredentialTokenService service = serviceAt(NOW);
        CredentialTokenService.TokenBinding binding = binding("task-1");
        CredentialTokenService.IssuedToken revoked = service.issue(
                binding, "user", "secret".toCharArray(), Duration.ofMinutes(2));
        service.revoke(revoked.jti());

        assertThrows(IllegalStateException.class, () -> service.consume(revoked.token(), binding));

        CredentialTokenService.IssuedToken expired = service.issue(
                binding, "user", "secret".toCharArray(), Duration.ofSeconds(1));
        CredentialTokenService expiredService = serviceAt(NOW.plusSeconds(2), service.store());
        assertThrows(IllegalStateException.class, () -> expiredService.consume(expired.token(), binding));
    }

    @Test
    void timeoutDoesNotPermitSameJtiToBeConsumedAgain() {
        CredentialTokenService service = serviceAt(NOW);
        CredentialTokenService.TokenBinding binding = binding("task-1");
        CredentialTokenService.IssuedToken token = service.issue(
                binding, "user", "secret".toCharArray(), Duration.ofMinutes(2));

        CredentialTokenService.TransientCredential credential = service.consume(token.token(), binding);
        credential.clear();

        assertThrows(IllegalStateException.class, () -> service.consume(token.token(), binding));
    }

    private CredentialTokenService serviceAt(Instant instant) {
        return serviceAt(instant, new CredentialTokenService.InMemoryTokenStore());
    }

    private CredentialTokenService serviceAt(Instant instant, CredentialTokenService.TokenStore store) {
        return new CredentialTokenService(Clock.fixed(instant, ZoneOffset.UTC), store);
    }

    private CredentialTokenService.TokenBinding binding(String taskId) {
        return new CredentialTokenService.TokenBinding(
                taskId, "device-1", "SSH", "template-1", "v1", "DEVICE_OPS");
    }
}
