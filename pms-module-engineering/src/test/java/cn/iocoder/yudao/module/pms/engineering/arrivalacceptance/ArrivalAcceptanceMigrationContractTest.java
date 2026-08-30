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
    private static String qualificationUpgradeSql;
    private static String fileFactUpgradeSql;
    private static String differenceFactUpgradeSql;
    private static String evidenceOutboxJobSql;
    private static String evidenceCorrelationUpgradeSql;
    private static String evidenceRetryJobSql;
    private static String task5BUpgradeSql;
    private static String successorIdentityUpgradeSql;
    private static String seedSql;

    @BeforeAll
    static void loadSchema() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        schemaSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V133__fimp002_arrival_acceptance.sql"), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        qualificationUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V134__fimp002_project_qualification_versions.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        fileFactUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V135__fimp002_file_fact_versions.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        differenceFactUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V136__fimp002_nullable_difference_fact_version.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        evidenceOutboxJobSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V137__fimp002_arrival_evidence_outbox_job.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        evidenceCorrelationUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V138__fimp002_evidence_correlation.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        evidenceRetryJobSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V139__fimp002_arrival_evidence_retry_job.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        task5BUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V140__fimp002_task5b_successor_fact_impact.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        successorIdentityUpgradeSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V141__fimp002_successor_batch_identity.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        seedSql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V142__fimp002_arrival_acceptance_seed.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    void definesOnlyTheFiveApprovedImpOwnerTables() {
        assertEquals(5, occurrences(schemaSql, "CREATE TABLE `imp_"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_acceptance`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_line`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_arrival_difference`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_delivery_evidence`"));
        assertTrue(schemaSql.contains("CREATE TABLE `imp_delivery_evidence_revision`"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
        assertFalse(schemaSql.contains("REFERENCES `com_"));
        assertFalse(schemaSql.contains("REFERENCES `ast_"));
        assertFalse(schemaSql.contains("REFERENCES `plt_"));
    }

    @Test
    void locksTenantScopedIdentityCurrentVersionsAndFactVersions() {
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_imp_arrival_batch` "
                + "(`tenant_id`, `project_id`, `batch_code`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_imp_arrival_project_fact` "
                + "(`tenant_id`, `project_id`, `project_fact_version`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_imp_arrival_line_revision` "
                + "(`tenant_id`, `arrival_acceptance_id`, `line_no`, `line_revision`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_imp_arrival_line_current` "
                + "(`tenant_id`, `arrival_acceptance_id`, `line_no`, `current_marker`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_imp_arrival_difference_current` "
                + "(`tenant_id`, `arrival_acceptance_id`, `difference_no`, `current_marker`)"));
        assertTrue(schemaSql.contains("CHECK (`current_marker` IS NULL OR `current_marker` = 1)"));
        assertEquals(7, occurrences(schemaSql, "FOREIGN KEY (`tenant_id`,"));
        assertTrue(schemaSql.contains("REFERENCES `imp_arrival_acceptance` (`tenant_id`, `id`)"));
        assertTrue(schemaSql.contains("REFERENCES `imp_delivery_evidence_revision` "
                + "(`tenant_id`, `evidence_id`, `revision_no`)"));
    }

    @Test
    void constrainsBatchLineDifferenceAndEvidenceStateAxes() {
        assertTrue(schemaSql.contains("'DRAFT', 'PARTIALLY_ACCEPTED', 'DIFFERENCE_PENDING', "
                + "'ACCEPTED', 'CONFIRMED'"));
        assertTrue(schemaSql.contains("'NOT_ARRIVED', 'ACCEPTED', 'DIFFERENCE_PENDING', 'REJECTED'"));
        assertTrue(schemaSql.contains("'OPEN', 'SUPPLEMENTED', 'REJECTED', 'EXEMPTED', 'CLOSED'"));
        assertTrue(schemaSql.contains("'NOT_PUBLISHED', 'PUBLISHED_PENDING_ACC', "
                + "'ARCHIVE_PENDING_RETRY', 'ACCEPTED_PENDING_ARCHIVE', "
                + "'ARCHIVE_ACK_PENDING_RETRY', 'ARCHIVED'"));
        assertTrue(schemaSql.contains("`acc_next_retry_at` datetime DEFAULT NULL"));
        assertTrue(schemaSql.contains("`acc_retry_count` int NOT NULL DEFAULT 0"));
        assertTrue(schemaSql.contains("`acc_last_event_id` varchar(128) DEFAULT NULL"));
    }

    @Test
    void schemaDoesNotPerformLegacyForwardMigrationOrStoreRawUrls() {
        assertFalse(schemaSql.contains("INSERT INTO"));
        assertFalse(schemaSql.contains("pms_eng_arrival"));
        assertFalse(schemaSql.contains("pms_eng_deliverable"));
        assertFalse(schemaSql.toLowerCase().contains("attachment_url"));
        assertFalse(schemaSql.toLowerCase().contains("download_url"));
        assertFalse(schemaSql.toLowerCase().contains("auto_assign"));
    }

    @Test
    void upgradesEmptyArrivalRootWithRequiredQualificationVersionsWithoutDefaults() {
        assertTrue(qualificationUpgradeSql.contains(
                "ADD COLUMN `project_version` int NOT NULL"));
        assertTrue(qualificationUpgradeSql.contains(
                "ADD COLUMN `project_participant_fact_version` bigint NOT NULL"));
        assertTrue(qualificationUpgradeSql.contains(
                "ADD COLUMN `project_scope_version` bigint NOT NULL"));
        assertFalse(qualificationUpgradeSql.contains(" DEFAULT "));
        assertFalse(schemaSql.contains("`project_participant_fact_version`"));
    }

    @Test
    void rejectsNonEmptyArrivalRootBeforeAlteringData() {
        int guard = qualificationUpgradeSql.indexOf(
                "IF EXISTS (SELECT 1 FROM `imp_arrival_acceptance` LIMIT 1)");
        int signal = qualificationUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = qualificationUpgradeSql.indexOf("ALTER TABLE `imp_arrival_acceptance`");
        assertTrue(guard >= 0);
        assertTrue(signal > guard);
        assertTrue(alter > signal);
        assertFalse(qualificationUpgradeSql.contains("UPDATE `imp_arrival_acceptance`"));
    }

    @Test
    void upgradesEmptyEvidenceRevisionWithFrozenFileFactsWithoutDefaults() {
        assertTrue(fileFactUpgradeSql.contains("ADD COLUMN `file_artifact_id` bigint NOT NULL"));
        assertTrue(fileFactUpgradeSql.contains("ADD COLUMN `file_scope_version` bigint NOT NULL"));
        assertTrue(fileFactUpgradeSql.contains("ADD COLUMN `file_fact_version` json NOT NULL"));
        assertTrue(fileFactUpgradeSql.contains("JSON_LENGTH(`file_fact_version`) = 3"));
        assertTrue(fileFactUpgradeSql.contains("'$.artifactVersion'"));
        assertTrue(fileFactUpgradeSql.contains("'$.referenceVersion'"));
        assertTrue(fileFactUpgradeSql.contains("'$.availabilityVersion'"));
        assertFalse(fileFactUpgradeSql.contains(" DEFAULT "));
    }

    @Test
    void rejectsNonEmptyEvidenceRevisionBeforeAlteringData() {
        int guard = fileFactUpgradeSql.indexOf(
                "IF EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` LIMIT 1)");
        int signal = fileFactUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = fileFactUpgradeSql.indexOf("ALTER TABLE `imp_delivery_evidence_revision`");
        assertTrue(guard >= 0);
        assertTrue(signal > guard);
        assertTrue(alter > signal);
        assertFalse(fileFactUpgradeSql.contains("UPDATE `imp_delivery_evidence_revision`"));
    }

    @Test
    void makesDifferenceFactVersionNullableWithoutDefaultOrBackfill() {
        assertTrue(differenceFactUpgradeSql.contains(
                "MODIFY COLUMN `project_fact_version` bigint NULL"));
        assertTrue(differenceFactUpgradeSql.contains(
                "`project_fact_version` IS NULL OR `project_fact_version` >= 0"));
        assertFalse(differenceFactUpgradeSql.contains(" DEFAULT "));
        assertFalse(differenceFactUpgradeSql.contains("UPDATE `imp_arrival_difference`"));
    }

    @Test
    void rejectsNonEmptyDifferenceTableBeforeChangingNullability() {
        int guard = differenceFactUpgradeSql.indexOf(
                "IF EXISTS (SELECT 1 FROM `imp_arrival_difference` LIMIT 1)");
        int signal = differenceFactUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = differenceFactUpgradeSql.indexOf("ALTER TABLE `imp_arrival_difference`");
        assertTrue(guard >= 0);
        assertTrue(signal > guard);
        assertTrue(alter > signal);
    }

    @Test
    void registersEvidenceOutboxJobPausedUntilAccConsumerIsReady() {
        assertTrue(evidenceOutboxJobSql.contains("992602010001"));
        assertTrue(evidenceOutboxJobSql.contains("'arrivalEvidenceOutboxDeliveryJob'"));
        assertTrue(evidenceOutboxJobSql.contains("'0/30 * * * * ?'"));
        assertTrue(evidenceOutboxJobSql.contains("WHERE NOT EXISTS"));
        assertTrue(evidenceOutboxJobSql.contains("`status` = 2"));
        assertFalse(evidenceOutboxJobSql.contains("`status` = 1"));
        assertFalse(evidenceOutboxJobSql.contains("arrivalEvidenceRetryJob"));
    }

    @Test
    void addsNullableEvidenceCorrelationWithoutDefaultOrInventedBackfill() {
        assertTrue(evidenceCorrelationUpgradeSql.contains(
                "ADD COLUMN `acc_correlation_id` varchar(128) NULL"));
        assertTrue(evidenceCorrelationUpgradeSql.contains(
                "`acc_sync_status` = 'NOT_PUBLISHED' AND `acc_correlation_id` IS NULL"));
        assertTrue(evidenceCorrelationUpgradeSql.contains(
                "`acc_sync_status` <> 'NOT_PUBLISHED' AND `acc_correlation_id` IS NOT NULL"));
        assertTrue(evidenceCorrelationUpgradeSql.contains(
                "CHAR_LENGTH(TRIM(`acc_correlation_id`)) BETWEEN 1 AND 128"));
        assertTrue(evidenceCorrelationUpgradeSql.contains(
                "CHAR_LENGTH(`acc_correlation_id`) = CHAR_LENGTH(TRIM(`acc_correlation_id`))"));
        assertFalse(evidenceCorrelationUpgradeSql.contains(" DEFAULT "));
        assertFalse(evidenceCorrelationUpgradeSql.contains("UPDATE `imp_delivery_evidence`"));
    }

    @Test
    void rejectsExistingPublishedEvidenceBeforeAddingCorrelationColumn() {
        int cleanup = evidenceCorrelationUpgradeSql.indexOf(
                "DROP PROCEDURE IF EXISTS `fimp002_require_unpublished_delivery_evidence`");
        int create = evidenceCorrelationUpgradeSql.indexOf(
                "CREATE PROCEDURE `fimp002_require_unpublished_delivery_evidence`");
        int guard = evidenceCorrelationUpgradeSql.indexOf(
                "WHERE `acc_sync_status` <> 'NOT_PUBLISHED'");
        int signal = evidenceCorrelationUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = evidenceCorrelationUpgradeSql.indexOf("ALTER TABLE `imp_delivery_evidence`");
        assertTrue(cleanup >= 0);
        assertTrue(create > cleanup);
        assertTrue(guard > create);
        assertTrue(signal > guard);
        assertTrue(alter > signal);
    }

    @Test
    void registersEvidenceRetryJobPausedUntilAccConsumerIsReady() {
        assertTrue(evidenceRetryJobSql.contains("992602010002"));
        assertTrue(evidenceRetryJobSql.contains("'arrivalEvidenceRetryJob'"));
        assertTrue(evidenceRetryJobSql.contains("'0 0/1 * * * ?'"));
        assertTrue(evidenceRetryJobSql.contains("WHERE NOT EXISTS"));
        assertTrue(evidenceRetryJobSql.contains("`status` = 2"));
        assertFalse(evidenceRetryJobSql.contains("`status` = 1"));
        assertFalse(evidenceRetryJobSql.contains("syncEnabledJobByHandlerName"));
    }

    @Test
    void addsServerOwnedSuccessorAndFactImpactDiscriminators() {
        assertTrue(task5BUpgradeSql.contains(
                "ADD COLUMN `successor_reason` varchar(32) NULL DEFAULT NULL"));
        assertTrue(task5BUpgradeSql.contains(
                "'SUPPLEMENT', 'CORRECTION', 'DIFFERENCE_CLOSURE', 'EXEMPTION_INVALIDATION'"));
        assertTrue(task5BUpgradeSql.contains(
                "ADD COLUMN `fact_impact_type` varchar(32) NULL DEFAULT NULL"));
        assertTrue(task5BUpgradeSql.contains("'CORRECTION', 'REOPEN', 'EXEMPTION_INVALIDATION'"));
        assertTrue(task5BUpgradeSql.contains("chk_imp_arrival_successor_pair"));
        assertTrue(task5BUpgradeSql.contains("chk_imp_arrival_difference_fact_pair"));
        assertFalse(task5BUpgradeSql.contains("UPDATE `imp_arrival_"));
    }

    @Test
    void failsBeforeTask5BAlterWhenImmutableHistoryCannotBeProvenAndCanBeReplayed() {
        int cleanup = task5BUpgradeSql.indexOf(
                "DROP PROCEDURE IF EXISTS `fimp002_require_provable_task5b_history`");
        int create = task5BUpgradeSql.indexOf(
                "CREATE PROCEDURE `fimp002_require_provable_task5b_history`");
        int predecessorGuard = task5BUpgradeSql.indexOf(
                "WHERE `predecessor_acceptance_id` IS NOT NULL");
        int factGuard = task5BUpgradeSql.indexOf(
                "WHERE `project_fact_version` IS NOT NULL");
        int signal = task5BUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = task5BUpgradeSql.indexOf("ALTER TABLE `imp_arrival_acceptance`");
        assertTrue(cleanup >= 0);
        assertTrue(create > cleanup);
        assertTrue(predecessorGuard > create);
        assertTrue(factGuard > predecessorGuard);
        assertTrue(signal > factGuard);
        assertTrue(alter > signal);
        assertEquals(2, occurrences(task5BUpgradeSql,
                "DROP PROCEDURE IF EXISTS `fimp002_require_provable_task5b_history`"));
    }

    @Test
    void replacesBatchUniquenessWithOneInitialRootAndLinearSuccessorChain() {
        assertTrue(successorIdentityUpgradeSql.contains(
                "ADD COLUMN `batch_root_marker` tinyint NULL"));
        assertFalse(successorIdentityUpgradeSql.contains("`batch_root_marker` tinyint NULL DEFAULT"));
        assertTrue(successorIdentityUpgradeSql.contains(
                "DROP INDEX `uk_imp_arrival_batch`"));
        assertTrue(successorIdentityUpgradeSql.contains(
                "(`tenant_id`, `project_id`, `batch_code`, `batch_root_marker`)"));
        assertTrue(successorIdentityUpgradeSql.contains(
                "(`tenant_id`, `predecessor_acceptance_id`)"));
        assertTrue(successorIdentityUpgradeSql.contains(
                "`predecessor_acceptance_id` IS NULL AND `successor_reason` IS NULL "
                        + "AND `batch_root_marker` IS NOT NULL AND `batch_root_marker` = 1"));
        assertTrue(successorIdentityUpgradeSql.contains(
                "`predecessor_acceptance_id` IS NOT NULL AND `successor_reason` IS NOT NULL AND `batch_root_marker` IS NULL"));
    }

    @Test
    void failsBeforeSuccessorIdentityAlterAndCanRerunAfterReconciliation() {
        int cleanup = successorIdentityUpgradeSql.indexOf(
                "DROP PROCEDURE IF EXISTS `fimp002_require_initial_arrival_roots`");
        int create = successorIdentityUpgradeSql.indexOf(
                "CREATE PROCEDURE `fimp002_require_initial_arrival_roots`");
        int predecessorGuard = successorIdentityUpgradeSql.indexOf(
                "WHERE `predecessor_acceptance_id` IS NOT NULL OR `successor_reason` IS NOT NULL");
        int signal = successorIdentityUpgradeSql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = successorIdentityUpgradeSql.indexOf("ALTER TABLE `imp_arrival_acceptance`");
        int update = successorIdentityUpgradeSql.indexOf("UPDATE `imp_arrival_acceptance`");
        assertTrue(cleanup >= 0);
        assertTrue(create > cleanup);
        assertTrue(predecessorGuard > create);
        assertTrue(signal > predecessorGuard);
        assertTrue(alter > signal);
        assertTrue(update > alter);
        assertEquals(2, occurrences(successorIdentityUpgradeSql,
                "DROP PROCEDURE IF EXISTS `fimp002_require_initial_arrival_roots`"));
    }

    @Test
    void seedsOnlyLockedArrivalStatesDifferenceTypesAndFivePermissions() {
        assertTrue(seedSql.contains("'pms_arrival_acceptance_status'"));
        assertTrue(seedSql.contains("'DRAFT'"));
        assertTrue(seedSql.contains("'PARTIALLY_ACCEPTED'"));
        assertTrue(seedSql.contains("'DIFFERENCE_PENDING'"));
        assertTrue(seedSql.contains("'ACCEPTED'"));
        assertTrue(seedSql.contains("'CONFIRMED'"));
        assertTrue(seedSql.contains("'pms_arrival_difference_type'"));
        assertTrue(seedSql.contains("'QUANTITY_MISMATCH'"));
        assertTrue(seedSql.contains("'MODEL_OR_SN_MISMATCH'"));
        assertTrue(seedSql.contains("'APPEARANCE_OR_QUALITY'"));
        assertTrue(seedSql.contains("'EVIDENCE_INCOMPLETE'"));
        assertEquals(2, occurrences(seedSql, "'pms:arrival-acceptance:query'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:create'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:edit-own-draft'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:confirm'"));
        assertTrue(seedSql.contains("'pms:arrival-acceptance:resolve-difference'"));
    }

    @Test
    void preservesLegacyArrivalAndRegistersReconciliationJobPausedWithoutBusinessSeeds() {
        assertTrue(seedSql.contains("'arrivalLegacyReconciliationJob'"));
        assertTrue(seedSql.contains("'0 0/5 * * * ?'"));
        assertTrue(seedSql.contains("WHERE NOT EXISTS"));
        assertTrue(seedSql.contains("`status`=2"));
        assertFalse(seedSql.contains("`status`=1"));
        assertFalse(seedSql.contains("19013"));
        assertFalse(seedSql.contains("pms:eng-arrival"));
        assertFalse(seedSql.contains("system_role_menu"));
        assertFalse(seedSql.contains("imp_arrival_acceptance`"));
        assertFalse(seedSql.toLowerCase().contains("auto_assign"));
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
