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
