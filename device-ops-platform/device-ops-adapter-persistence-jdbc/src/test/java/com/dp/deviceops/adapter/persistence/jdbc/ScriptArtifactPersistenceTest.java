package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.ScriptArtifactRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ScriptArtifactPersistenceTest {
    @Test
    void persistsRegisteredVersionsIdempotentlyAndRejectsContentConflicts() throws Exception {
        DataSource dataSource = fileDataSource(Files.createTempDirectory("device-ops-h2"));
        migrate(dataSource);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertEquals(2, jdbc.sql("select count(*) from information_schema.tables where table_schema = 'PUBLIC' and table_name in ('DEVICE_OPS_SCRIPT', 'DEVICE_OPS_SCRIPT_VERSION')")
                .query(Integer.class).single());
        assertEquals(0, jdbc.sql("select count(*) from information_schema.tables where table_schema = 'PUBLIC' and table_name in ('DEVICE_OPS_PROJECT', 'DEVICE_OPS_DEVICE')")
                .query(Integer.class).single());
        ScriptArtifactRepository repository = new JdbcScriptArtifactRepository(jdbc);
        ScriptArtifact artifact = artifact("show version", "1");

        repository.save("pms", artifact);
        repository.save("pms", artifact);
        ScriptArtifact loaded = repository.find("pms", "inventory", "1").orElseThrow();

        assertEquals(artifact.key(), loaded.key());
        assertEquals(artifact.version(), loaded.version());
        assertEquals(artifact.content(), loaded.content());
        assertEquals(artifact.sha256(), loaded.sha256());
        assertEquals(artifact.source(), loaded.source());
        assertEquals(artifact.persistencePolicy(), loaded.persistencePolicy());
        assertEquals(artifact.parserType(), loaded.parserType());
        assertEquals(artifact.parserConfig(), loaded.parserConfig());
        assertThrows(ScriptArtifact.DomainConflictException.class,
                () -> repository.save("pms", artifact("show clock", "1")));
    }

    @Test
    void doesNotPersistExecutionOnlyArtifactsAndReusesFileMigrationHistory() throws Exception {
        Path database = Files.createTempDirectory("device-ops-h2").resolve("scripts");
        DataSource firstDataSource = fileDataSource(database);
        migrate(firstDataSource);
        ScriptArtifactRepository firstRepository = new JdbcScriptArtifactRepository(JdbcClient.create(firstDataSource));
        ScriptArtifact registered = artifact("show version", "1");
        firstRepository.save("pms", registered);
        firstRepository.save("pms", ScriptArtifact.adHoc("inventory", "transient", "show version", sha256("show version"), "NONE", null));

        DataSource secondDataSource = fileDataSource(database);
        migrate(secondDataSource);
        JdbcClient secondJdbc = JdbcClient.create(secondDataSource);
        ScriptArtifactRepository secondRepository = new JdbcScriptArtifactRepository(secondJdbc);
        ScriptArtifact reloaded = secondRepository.find("pms", "inventory", "1").orElseThrow();

        assertEquals(registered.key(), reloaded.key());
        assertEquals(registered.version(), reloaded.version());
        assertEquals(registered.content(), reloaded.content());
        assertTrue(secondRepository.find("pms", "inventory", "transient").isEmpty());
        assertEquals(1, secondJdbc.sql("select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = true")
                .query(Integer.class).single());
    }

    private static DataSource fileDataSource(Path file) {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:file:" + file.toAbsolutePath() + ";MODE=MySQL");
        return source;
    }
    private static void migrate(DataSource dataSource) { Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate(); }
    private static ScriptArtifact artifact(String content, String version) { return ScriptArtifact.external("inventory", version, content, sha256(content), ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "KEY_VALUE", "strict=true"); }
    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
