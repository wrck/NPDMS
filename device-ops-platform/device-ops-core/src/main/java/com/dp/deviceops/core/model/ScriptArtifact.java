package com.dp.deviceops.core.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Immutable script bytes and parsing metadata for a single collection execution.
 */
public final class ScriptArtifact {

    public enum ScriptSource {
        LOCAL_MANAGED,
        EXTERNAL_DELIVERED,
        ADHOC_INLINE
    }

    public enum PersistencePolicy {
        EXECUTION_ONLY,
        REGISTER_VERSION
    }

    public static class DomainConflictException extends RuntimeException {

        public DomainConflictException(String message) {
            super(message);
        }
    }

    private final ScriptSource source;
    private final String key;
    private final String version;
    private final String content;
    private final String sha256;
    private final PersistencePolicy persistencePolicy;
    private final String parserType;
    private final String parserConfig;

    private ScriptArtifact(ScriptSource source, String key, String version, String content, String sha256,
                           PersistencePolicy persistencePolicy, String parserType, String parserConfig) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.key = requireText(key, "key");
        this.version = requireText(version, "version");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.sha256 = validateSha256(content, sha256);
        this.persistencePolicy = Objects.requireNonNull(persistencePolicy, "persistencePolicy must not be null");
        this.parserType = requireText(parserType, "parserType");
        this.parserConfig = parserConfig;
    }

    public static ScriptArtifact local(String key, String version, String content, String sha256,
                                       PersistencePolicy persistencePolicy, String parserType, String parserConfig) {
        return new ScriptArtifact(ScriptSource.LOCAL_MANAGED, key, version, content, sha256,
                persistencePolicy, parserType, parserConfig);
    }

    public static ScriptArtifact external(String key, String version, String content, String sha256,
                                          PersistencePolicy persistencePolicy, String parserType, String parserConfig) {
        return new ScriptArtifact(ScriptSource.EXTERNAL_DELIVERED, key, version, content, sha256,
                persistencePolicy, parserType, parserConfig);
    }

    public static ScriptArtifact adHoc(String key, String version, String content, String sha256,
                                       String parserType, String parserConfig) {
        return new ScriptArtifact(ScriptSource.ADHOC_INLINE, key, version, content, sha256,
                PersistencePolicy.EXECUTION_ONLY, parserType, parserConfig);
    }

    public void assertSameVersion(ScriptArtifact candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (persistencePolicy == PersistencePolicy.REGISTER_VERSION
                && candidate.persistencePolicy == PersistencePolicy.REGISTER_VERSION
                && key.equals(candidate.key) && version.equals(candidate.version)
                && (!content.equals(candidate.content) || !sha256.equals(candidate.sha256))) {
            throw new DomainConflictException("registered script version content cannot change");
        }
    }

    public ScriptSource source() {
        return source;
    }

    public String key() {
        return key;
    }

    public String version() {
        return version;
    }

    public String content() {
        return content;
    }

    public String sha256() {
        return sha256;
    }

    public PersistencePolicy persistencePolicy() {
        return persistencePolicy;
    }

    public String parserType() {
        return parserType;
    }

    public String parserConfig() {
        return parserConfig;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String validateSha256(String content, String value) {
        String sha256 = requireText(value, "sha256");
        if (!sha256.matches("[0-9a-fA-F]{64}") || !sha256.equalsIgnoreCase(sha256(content))) {
            throw new IllegalArgumentException("sha256 must match content");
        }
        return sha256.toLowerCase();
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
