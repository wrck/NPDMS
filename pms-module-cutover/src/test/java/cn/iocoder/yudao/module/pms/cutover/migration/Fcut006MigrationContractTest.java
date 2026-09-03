package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut006MigrationContractTest {
    private final String sql = readMigration();
    private final String jobSql = readJobMigration();

    @Test
    void preflightsBeforeCreatingClosureTablesAndNeverUpdatesExistingRows() {
        int preflight = sql.indexOf("CALL `fcut006_require_stage_contract`()");
        int firstBusinessDdl = sql.indexOf("ALTER TABLE `cut_task`");
        assertThat(preflight).isPositive().isLessThan(firstBusinessDdl);
        assertThat(sql).contains("F-CUT-006 cut_task stage preflight failed")
                .contains("F-CUT-006 stage history preflight failed")
                .doesNotContain("UPDATE `cut_task`")
                .doesNotContain("UPDATE `cut_task_stage_history`");
    }

    @Test
    void createsOnlyTheThreeLockedClosureTablesAndForwardTerminalState() {
        assertThat(sql).contains("CREATE TABLE `cut_cutover_closure`")
                .contains("CREATE TABLE `cut_cutover_closure_attachment`")
                .contains("CREATE TABLE `cut_cutover_collection_evidence`")
                .contains("'CLOSURE_IN_PROGRESS','ARCHIVED'")
                .contains("'P6_CLOSURE_SUBMITTED'")
                .contains("`from_stage` = 'P6'")
                .contains("`to_stage` = 'P6'")
                .doesNotContain("pms_cut_execution")
                .doesNotContain("pms_cut_observation");
    }

    @Test
    void locksClosureResultAttachmentAndAppendOnlyEvidenceUnions() {
        assertThat(sql).contains("UNIQUE KEY `uk_cut_closure_task`")
                .contains("`pre_check_detail` text DEFAULT NULL")
                .contains("CHAR_LENGTH(`legacy_items`) <= 4000")
                .contains("COALESCE(CHAR_LENGTH(TRIM(`pre_check_detail`)) BETWEEN 1 AND 4000, FALSE)")
                .contains("CHAR_LENGTH(TRIM(`rollback_reason`)) BETWEEN 1 AND 4000), FALSE")
                .contains("`result_ref` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
                .contains("CAST(`result_ref` AS BINARY) = CAST(CONCAT('CUTOVER_CLOSURE:', `id`, ':', `version`) AS BINARY)")
                .contains("`final_result_code` = 'FAILED' AND `result_ref` IS NULL")
                .contains("UNIQUE KEY `uk_cut_closure_attachment`")
                .contains("`reference_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
                .contains("`file_hash` varchar(64) CHARACTER SET ascii COLLATE ascii_bin")
                .contains("REGEXP_LIKE(`file_hash`, _ascii'^[0-9a-f]{64}$', 'c')")
                .contains("GENERATED ALWAYS AS (CASE WHEN `evidence_type_code` IN ('DISPATCH_ACCEPTED','DISPATCH_FAILED')")
                .contains("UNIQUE KEY `uk_cut_collection_callback_event`")
                .contains("`collection_task_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
                .contains("`callback_event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
                .contains("CAST(`collection_task_id` AS BINARY) = CAST(`original_failed_collection_task_id` AS BINARY)");
    }

    @Test
    void registersLegacyClosureReconciliationJobPausedAndWithoutActivation() {
        assertThat(jobSql).contains("'legacyCutoverClosureReconciliationJob'")
                .contains("'割接闭环旧数据核对', 2")
                .contains("SET `name`='割接闭环旧数据核对', `status`=2")
                .doesNotContain("status`=1")
                .doesNotContain("QRTZ_");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V186__fcut006_p6_cutover_closure.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readJobMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V187__fcut006_legacy_closure_job.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
