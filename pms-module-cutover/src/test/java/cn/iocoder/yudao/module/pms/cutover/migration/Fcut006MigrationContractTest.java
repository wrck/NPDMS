package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut006MigrationContractTest {
    private final String sql = readMigration();

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
                .contains("`result_ref` = CONCAT('CUTOVER_CLOSURE:', `id`, ':', `version`)")
                .contains("`final_result_code` = 'FAILED' AND `result_ref` IS NULL")
                .contains("UNIQUE KEY `uk_cut_closure_attachment`")
                .contains("`file_hash` REGEXP '^[0-9a-f]{64}$'")
                .contains("GENERATED ALWAYS AS (CASE WHEN `evidence_type_code` IN ('DISPATCH_ACCEPTED','DISPATCH_FAILED')")
                .contains("UNIQUE KEY `uk_cut_collection_callback_event`")
                .contains("`collection_task_id` = `original_failed_collection_task_id`");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V155__fcut006_p6_cutover_closure.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
