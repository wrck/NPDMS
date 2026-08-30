package cn.iocoder.yudao.module.pms.platform.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformMigrationEvidenceMigrationContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        sql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V144__platform_migration_evidence.sql"), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    @Test
    void createsExactlyFourPlatformOwnerTables() {
        assertEquals(4, occurrences(sql, "CREATE TABLE `plt_"));
        assertTrue(sql.contains("CREATE TABLE `plt_migration_batch`"));
        assertTrue(sql.contains("CREATE TABLE `plt_migration_source_record`"));
        assertTrue(sql.contains("CREATE TABLE `plt_external_key_mapping`"));
        assertTrue(sql.contains("CREATE TABLE `plt_migration_issue`"));
        assertFalse(sql.contains("com_"));
    }

    @Test
    void locksBatchLifecycleFailureAndFinalCounts() {
        assertTrue(sql.contains("'IMPORTING', 'STAGED_READY', 'RECONCILING', 'COMPLETED', 'FAILED'"));
        assertTrue(sql.contains("'MANIFEST_STRUCTURE_INVALID', 'MANIFEST_ROW_COUNT_MISMATCH'"));
        assertTrue(sql.contains("`source_count` = `mapped_count` + `issue_count` + `retained_count`"));
        assertTrue(sql.contains("`batch_status` <> 'COMPLETED' AND `mapped_count` = 0"));
        assertTrue(sql.contains("UNIQUE KEY `uk_plt_migration_batch_identity`"));
    }

    @Test
    void keepsSourceRowsImmutableAndBatchScoped() {
        assertTrue(sql.contains("UNIQUE KEY `uk_plt_migration_source_identity` "
                + "(`tenant_id`, `batch_id`, `source_system`, `source_table`, `source_record_key`)"));
        assertTrue(sql.contains("KEY `idx_plt_migration_source_cursor` (`tenant_id`, `batch_id`, `id`)"));
        assertFalse(sql.contains("UPDATE `plt_migration_source_record`"));
    }

    @Test
    void enforcesMappedRetainedUnionAndStableResultIdentity() {
        assertTrue(sql.contains("CASE WHEN `result_type` = 'RETAINED' THEN 'RETAINED'"));
        assertTrue(sql.contains("UNIQUE KEY `uk_plt_external_mapping_result` "
                + "(`tenant_id`, `source_record_id`, `result_key`)"));
        assertTrue(sql.contains("`result_type` = 'MAPPED' AND `target_context` IS NOT NULL"));
        assertTrue(sql.contains("`result_type` = 'RETAINED' AND `target_context` IS NULL"));
    }

    @Test
    void enforcesAppendOnlyIssueClosureShape() {
        assertTrue(sql.contains("UNIQUE KEY `uk_plt_migration_issue_key` "
                + "(`tenant_id`, `source_record_id`, `issue_key`)"));
        assertTrue(sql.contains("`issue_status` = 'OPEN' AND `resolver_user_id` IS NULL"));
        assertTrue(sql.contains("`issue_status` = 'CLOSED' AND `resolver_user_id` > 0"));
        assertFalse(sql.contains("ON DELETE CASCADE"));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int index = value.indexOf(token); index >= 0; index = value.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
