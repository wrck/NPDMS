package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.ScriptArtifactRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Objects;
import java.util.Optional;

public final class JdbcScriptArtifactRepository implements ScriptArtifactRepository {

    private final JdbcClient jdbc;

    public JdbcScriptArtifactRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public void save(String namespace, ScriptArtifact artifact) {
        requireText(namespace, "namespace");
        Objects.requireNonNull(artifact, "artifact must not be null");
        if (artifact.persistencePolicy() == ScriptArtifact.PersistencePolicy.EXECUTION_ONLY) {
            return;
        }
        long scriptId = findOrCreateScript(namespace, artifact);
        try {
            jdbc.sql("INSERT INTO device_ops_script_version (script_id, version, content, sha256, parser_type, parser_config) "
                            + "VALUES (:scriptId, :version, :content, :sha256, :parserType, :parserConfig)")
                    .param("scriptId", scriptId).param("version", artifact.version()).param("content", artifact.content())
                    .param("sha256", artifact.sha256()).param("parserType", artifact.parserType())
                    .param("parserConfig", artifact.parserConfig()).update();
        } catch (DuplicateKeyException ignored) {
            ScriptArtifact existing = find(namespace, artifact.key(), artifact.version()).orElseThrow();
            existing.assertSameVersion(artifact);
        }
    }

    @Override
    public Optional<ScriptArtifact> find(String namespace, String scriptKey, String version) {
        return jdbc.sql("SELECT s.source, s.script_key, v.version, v.content, v.sha256, v.parser_type, v.parser_config "
                        + "FROM device_ops_script s JOIN device_ops_script_version v ON v.script_id = s.id "
                        + "WHERE s.namespace = :namespace AND s.script_key = :scriptKey AND v.version = :version")
                .param("namespace", namespace).param("scriptKey", scriptKey).param("version", version)
                .query((rs, row) -> artifact(rs.getString("source"), rs.getString("script_key"), rs.getString("version"), rs.getString("content"),
                        rs.getString("sha256"), rs.getString("parser_type"), rs.getString("parser_config")))
                .optional();
    }

    private long findOrCreateScript(String namespace, ScriptArtifact artifact) {
        try {
            jdbc.sql("INSERT INTO device_ops_script (namespace, script_key, source) VALUES (:namespace, :scriptKey, :source)")
                    .param("namespace", namespace).param("scriptKey", artifact.key()).param("source", artifact.source().name()).update();
        } catch (DuplicateKeyException ignored) {
            // Another transaction registered this script key; the immutable version constraint below is authoritative.
        }
        return jdbc.sql("SELECT id FROM device_ops_script WHERE namespace = :namespace AND script_key = :scriptKey")
                .param("namespace", namespace).param("scriptKey", artifact.key()).query(Long.class).single();
    }

    private static ScriptArtifact artifact(String source, String key, String version, String content, String sha256,
                                           String parserType, String parserConfig) {
        return ScriptArtifact.ScriptSource.valueOf(source) == ScriptArtifact.ScriptSource.LOCAL_MANAGED
                ? ScriptArtifact.local(key, version, content, sha256, ScriptArtifact.PersistencePolicy.REGISTER_VERSION, parserType, parserConfig)
                : ScriptArtifact.external(key, version, content, sha256, ScriptArtifact.PersistencePolicy.REGISTER_VERSION, parserType, parserConfig);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
