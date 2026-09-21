package com.dp.deviceops.core.model;

import java.util.Arrays;
import java.util.Objects;

/** Write-only execution credential whose backing arrays are cleared on close. */
public final class TransientCredential implements AutoCloseable {
    private char[] secret;
    private char[] passphrase;
    private boolean closed;

    public TransientCredential(char[] secret, char[] passphrase) {
        this.secret = copyRequired(secret, "secret");
        this.passphrase = passphrase == null ? null : Arrays.copyOf(passphrase, passphrase.length);
    }

    private Thread activeThread;

    public synchronized <T> T withCredentials(CredentialOperation<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        if (closed) throw new IllegalStateException("credential is closed");
        char[] secretCopy = Arrays.copyOf(secret, secret.length);
        char[] passphraseCopy = passphrase == null ? null : Arrays.copyOf(passphrase, passphrase.length);
        activeThread = Thread.currentThread();
        try {
            return operation.apply(secretCopy, passphraseCopy);
        } finally {
            Arrays.fill(secretCopy, '\0');
            if (passphraseCopy != null) Arrays.fill(passphraseCopy, '\0');
            activeThread = null;
        }
    }

    @Override public synchronized void close() {
        if (activeThread == Thread.currentThread()) throw new IllegalStateException("credential cannot close during its active callback");
        if (!closed) {
            Arrays.fill(secret, '\0');
            if (passphrase != null) Arrays.fill(passphrase, '\0');
            closed = true;
        }
    }

    @Override public String toString() { return "TransientCredential[" + (closed ? "closed" : "redacted") + "]"; }

    boolean isCleared() { return closed && allZero(secret) && (passphrase == null || allZero(passphrase)); }
    private static char[] copyRequired(char[] value, String field) { Objects.requireNonNull(value, field + " must not be null"); if (value.length == 0) throw new IllegalArgumentException(field + " must not be empty"); return Arrays.copyOf(value, value.length); }
    private static boolean allZero(char[] value) { for (char character : value) if (character != '\0') return false; return true; }
    @FunctionalInterface public interface CredentialOperation<T> { T apply(char[] secret, char[] passphrase); }
}
