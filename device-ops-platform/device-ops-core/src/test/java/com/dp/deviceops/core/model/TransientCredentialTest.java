package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransientCredentialTest {
    @Test void clearsInternalAndCallbackCopiesWithoutExposingSecrets() {
        TransientCredential credential = new TransientCredential("secret-value".toCharArray(), "phrase".toCharArray());
        AtomicReference<char[]> callbackSecret = new AtomicReference<>();
        credential.withCredentials((secret, passphrase) -> { callbackSecret.set(secret); assertEquals("secret-value", String.valueOf(secret)); return null; });
        assertTrue(java.util.Arrays.equals(new char["secret-value".length()], callbackSecret.get()));
        credential.close(); credential.close();
        assertTrue(credential.isCleared());
        assertThrows(IllegalStateException.class, () -> credential.withCredentials((secret, passphrase) -> null));
        assertFalse(credential.toString().contains("secret-value"));
    }

    @Test void projectionContainsOnlyConnectionSnapshot() {
        ExecutionConnectionContext context = new ExecutionConnectionContext(new CommandExecutionPort.ConnectionSpec("host", 22, "operator", CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC, "SHA256:abc", Duration.ofSeconds(3)), new TransientCredential("secret-value".toCharArray(), null));
        assertEquals("host", context.projection().host());
        assertFalse(context.projection().toString().contains("secret-value"));
        context.closeCredential();
    }

    @Test void serializesCloseUntilCallbackCopiesAreCleared() throws Exception {
        TransientCredential credential = new TransientCredential("secret-value".toCharArray(), null);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<char[]> callbackCopy = new AtomicReference<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> use = executor.submit(() -> credential.withCredentials((secret, passphrase) -> {
                callbackCopy.set(secret); entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException exception) { throw new AssertionError(exception); }
                return null;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> close = executor.submit(credential::close);
            assertFalse(close.isDone());
            release.countDown();
            use.get(5, TimeUnit.SECONDS); close.get(5, TimeUnit.SECONDS);
        }
        assertTrue(java.util.Arrays.equals(new char["secret-value".length()], callbackCopy.get()));
        assertTrue(credential.isCleared());
    }

    @Test void rejectsReentrantCloseFromCallback() {
        TransientCredential credential = new TransientCredential("secret-value".toCharArray(), null);
        assertThrows(IllegalStateException.class, () -> credential.withCredentials((secret, passphrase) -> { credential.close(); return null; }));
        credential.close();
    }
}
