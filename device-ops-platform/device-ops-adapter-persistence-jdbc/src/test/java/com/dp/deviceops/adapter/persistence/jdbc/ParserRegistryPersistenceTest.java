package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserRegistryPersistenceTest {

    static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void invalidatesStaleValidationAndKeepsPublishedReleasesImmutable() {
        DataSource dataSource = migrated("registry");
        JdbcParserReleaseRepository repository = repository(dataSource);
        repository.createLogType(new LogType("show-tech", "Show Tech", "device evidence", NOW, NOW));
        ParserRelease first = draft("release-1", 1);
        repository.saveDraft(first, bundle());
        ParserReleaseValidation validation = validation(1);
        assertTrue(repository.saveValidation(validation));

        ParserRelease second = draft("release-1", 2);
        repository.saveDraft(second, bundle());

        assertFalse(repository.saveValidation(validation));
        assertEquals(null, repository.findRelease("release-1").orElseThrow().validation());
        assertTrue(repository.saveValidation(validation(2)));
        assertTrue(repository.publish("release-1", ReleaseState.DRAFT));
        assertEquals(ReleaseState.PUBLISHED, repository.findRelease("release-1").orElseThrow().state());
        assertThrows(IllegalStateException.class,
                () -> repository.saveDraft(draft("release-1", 3), bundle()));
    }

    @Test
    void activationUsesCompareAndSetForSwitchAndRollback() {
        DataSource dataSource = migrated("activation");
        JdbcParserReleaseRepository repository = repository(dataSource);
        repository.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        publish(repository, "release-1", "1.0.0");
        publish(repository, "release-2", "2.0.0");

        assertTrue(repository.activate("show-tech", "release-1", null));
        assertFalse(repository.activate("show-tech", "release-2", null));
        assertTrue(repository.activate("show-tech", "release-2", "release-1"));
        assertTrue(repository.activate("show-tech", "release-1", "release-2"));
        assertEquals("release-1", repository.findActive("show-tech").orElseThrow().releaseId());
    }

    @Test
    void roundTripsModelAwareArtifactsThroughTheExistingRulesColumn() {
        DataSource dataSource = migrated("model-artifact");
        JdbcParserReleaseRepository repository = repository(dataSource);
        repository.createLogType(new LogType("show-tech", "Show Tech", "", NOW, NOW));
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "show-tech", "1.1.0",
                "command-output-block/v1", "1.0.0", "1.0.0", "1.1.0", "1.1.0", "1.0.0", null);
        String profiles = """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},"profiles":[]}
                """;
        Map<String, String> ruleSets = Map.of("rules/base.json",
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.1.0\",\"rules\":[]}");
        ParserReleaseBundle expected = new ParserReleaseBundle(manifest, null,
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"profiles\":[]}",
                profiles, ruleSets, List.of(new ParserReleaseBundle.VerificationCase("case-1", "{}", "{}\n")));
        ParserRelease release = new ParserRelease("release-model", "show-tech", "1.1.0", ReleaseState.DRAFT,
                new ParserCoordinate("show-tech", "1.1.0", "1.1.0", "1.1.0", "1.0.0", null, null),
                1, null, NOW, null);

        repository.saveDraft(release, expected);
        ParserReleaseBundle actual = repository.loadBundle("release-model");

        assertEquals(expected.modelProfilesJson(), actual.modelProfilesJson());
        assertEquals(expected.ruleSetJsonByPath(), actual.ruleSetJsonByPath());
        assertEquals(expected.projectionsJson(), actual.projectionsJson());
    }

    static DataSource migrated(String name) {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:parser-" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return source;
    }

    static JdbcParserReleaseRepository repository(DataSource dataSource) {
        return new JdbcParserReleaseRepository(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static ParserReleaseBundle bundle() {
        return bundle("1.0.0");
    }

    static ParserReleaseBundle bundle(String version) {
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "show-tech", version,
                "command-output-block/v1", "1.0.0", "1.0.0", "1.0.0", "1.0.0", "1.0.0", null);
        return new ParserReleaseBundle(manifest,
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"rules\":[]}",
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"profiles\":[]}",
                List.of(new ParserReleaseBundle.VerificationCase("case-1", "{}", "{}\n")));
    }

    static ParserRelease draft(String releaseId, long revision) {
        return draft(releaseId, "1.0.0", revision);
    }

    static ParserRelease draft(String releaseId, String version, long revision) {
        return new ParserRelease(releaseId, "show-tech", version, ReleaseState.DRAFT,
                coordinate(version), revision, null, NOW, null);
    }

    static ParserCoordinate coordinate(String version) {
        return new ParserCoordinate("show-tech", version, "1.0.0", "1.0.0", "1.0.0", null, null);
    }

    private static ParserReleaseValidation validation(long revision) {
        return new ParserReleaseValidation("release-1", revision, true, 1, List.of(), NOW);
    }

    private static void publish(JdbcParserReleaseRepository repository, String releaseId, String version) {
        repository.saveDraft(draft(releaseId, version, 1), bundle(version));
        repository.saveValidation(new ParserReleaseValidation(releaseId, 1, true, 1, List.of(), NOW));
        repository.publish(releaseId, ReleaseState.DRAFT);
    }
}
