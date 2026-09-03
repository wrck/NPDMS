package cn.iocoder.yudao.module.pms.engineering.arrivalacceptance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrivalAcceptanceMigrationContractTest {

    private static String schemaSql;
    private static String qualificationSql;
    private static String fileFactSql;
    private static String differenceSql;
    private static String outboxJobSql;
    private static String correlationSql;
    private static String retryJobSql;
    private static String task5bSql;
    private static String successorSql;
    private static String seedSql;

    @BeforeAll
    static void loadMigrations() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        schemaSql = read(repositoryDirectory, "V193__fimp002_arrival_acceptance.sql");
        qualificationSql = read(repositoryDirectory, "V194__fimp002_project_qualification_versions.sql");
        fileFactSql = read(repositoryDirectory, "V195__fimp002_file_fact_versions.sql");
        differenceSql = read(repositoryDirectory, "V196__fimp002_nullable_difference_fact_version.sql");
        outboxJobSql = read(repositoryDirectory, "V197__fimp002_arrival_evidence_outbox_job.sql");
        correlationSql = read(repositoryDirectory, "V198__fimp002_evidence_correlation.sql");
        retryJobSql = read(repositoryDirectory, "V199__fimp002_arrival_evidence_retry_job.sql");
        task5bSql = read(repositoryDirectory, "V200__fimp002_task5b_successor_fact_impact.sql");
        successorSql = read(repositoryDirectory, "V201__fimp002_successor_batch_identity.sql");
        seedSql = read(repositoryDirectory, "V202__fimp002_arrival_acceptance_seed.sql");
    }

    @Test
    void createsOnlyFiveImpOwnerTablesAndPreservesLegacy() {
        assertEquals(5, occurrences(schemaSql, "CREATE TABLE `imp_"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_acceptance`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_line`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_difference`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_delivery_evidence`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_delivery_evidence_revision`"));
        assertFalse(schemaSql.contains("pms_eng_arrival"));
        assertFalse(schemaSql.contains("pms_eng_deliverable"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
        assertFalse(schemaSql.contains("REFERENCES `com_"));
        assertFalse(schemaSql.contains("REFERENCES `ast_"));
        assertFalse(schemaSql.contains("REFERENCES `plt_"));
    }

    @Test
    void freezesQualificationAndFileFactVersionsWithoutInventedDefaults() {
        assertTrue(qualificationSql.contains("ADD COLUMN `project_version` int NOT NULL"));
        assertTrue(qualificationSql.contains("ADD COLUMN `project_participant_fact_version` bigint NOT NULL"));
        assertTrue(qualificationSql.contains("ADD COLUMN `project_scope_version` bigint NOT NULL"));
        assertFalse(qualificationSql.contains("UPDATE `imp_arrival_acceptance`"));
        assertTrue(fileFactSql.contains("ADD COLUMN `file_artifact_id` bigint NOT NULL"));
        assertTrue(fileFactSql.contains("ADD COLUMN `file_scope_version` bigint NOT NULL"));
        assertTrue(fileFactSql.contains("ADD COLUMN `file_fact_version` json NOT NULL"));
        assertTrue(fileFactSql.contains("JSON_LENGTH(`file_fact_version`) = 3"));
        assertFalse(fileFactSql.contains("UPDATE `imp_delivery_evidence_revision`"));
    }

    @Test
    void keepsDifferenceFactVersionNullableAndEvidenceCorrelationProvable() {
        assertTrue(differenceSql.contains("MODIFY COLUMN `project_fact_version` bigint NULL"));
        assertTrue(differenceSql.contains("`project_fact_version` IS NULL OR `project_fact_version` >= 0"));
        assertFalse(differenceSql.contains("UPDATE `imp_arrival_difference`"));
        assertTrue(correlationSql.contains("ADD COLUMN `acc_correlation_id` varchar(128) NULL"));
        assertTrue(correlationSql.contains("CHAR_LENGTH(TRIM(`acc_correlation_id`)) BETWEEN 1 AND 128"));
        assertFalse(correlationSql.contains("UPDATE `imp_delivery_evidence`"));
    }

    @Test
    void registersAllJobsPausedUntilProductionDependenciesAreReady() {
        assertTrue(outboxJobSql.contains("'arrivalEvidenceOutboxDeliveryJob'"));
        assertTrue(outboxJobSql.contains("`status` = 2"));
        assertFalse(outboxJobSql.contains("`status` = 1"));
        assertTrue(retryJobSql.contains("'arrivalEvidenceRetryJob'"));
        assertTrue(retryJobSql.contains("`status` = 2"));
        assertFalse(retryJobSql.contains("`status` = 1"));
        assertTrue(seedSql.contains("'arrivalLegacyReconciliationJob'"));
        assertTrue(seedSql.contains("`status`=2"));
        assertFalse(seedSql.contains("`status`=1"));
    }

    @Test
    void addsServerOwnedSuccessorAndLinearBatchIdentity() {
        assertTrue(task5bSql.contains("ADD COLUMN `successor_reason` varchar(32) NULL DEFAULT NULL"));
        assertTrue(task5bSql.contains("'SUPPLEMENT', 'CORRECTION', 'DIFFERENCE_CLOSURE', 'EXEMPTION_INVALIDATION'"));
        assertTrue(task5bSql.contains("ADD COLUMN `fact_impact_type` varchar(32) NULL DEFAULT NULL"));
        assertTrue(task5bSql.contains("chk_imp_arrival_successor_pair"));
        assertFalse(task5bSql.contains("UPDATE `imp_arrival_"));
        assertTrue(successorSql.contains("ADD COLUMN `batch_root_marker` tinyint NULL"));
        assertTrue(successorSql.contains("DROP INDEX `uk_imp_arrival_batch`"));
        assertTrue(successorSql.contains("(`tenant_id`, `project_id`, `batch_code`, `batch_root_marker`)"));
        assertTrue(successorSql.contains("(`tenant_id`, `predecessor_acceptance_id`)"));
    }

    @Test
    void seedsOnlyLockedStatesPermissionsAndNoBusinessFacts() {
        assertTrue(seedSql.contains("'pms_arrival_acceptance_status'"));
        assertTrue(seedSql.contains("'DRAFT'"));
        assertTrue(seedSql.contains("'PARTIALLY_ACCEPTED'"));
        assertTrue(seedSql.contains("'DIFFERENCE_PENDING'"));
        assertTrue(seedSql.contains("'ACCEPTED'"));
        assertTrue(seedSql.contains("'CONFIRMED'"));
        assertTrue(seedSql.contains("'pms_arrival_difference_type'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:query'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:create'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:edit-own-draft'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:confirm'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:resolve-difference'"));
        assertFalse(seedSql.contains("system_role_menu"));
        assertFalse(seedSql.contains("imp_arrival_acceptance`"));
    }

    private static String read(Path repositoryDirectory, String fileName) throws IOException {
        return Files.readString(repositoryDirectory.resolve("sql/migrations").resolve(fileName),
                        StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
