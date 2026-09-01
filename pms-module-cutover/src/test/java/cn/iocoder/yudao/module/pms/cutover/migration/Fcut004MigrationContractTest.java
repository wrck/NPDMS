package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut004MigrationContractTest {
    private final String sql = readMigration();

    @Test
    void preflightsExistingStagesBeforeEverySchemaChange() {
        int preflight = sql.indexOf("CALL `fcut004_require_stage_contract`()");
        assertThat(preflight).isGreaterThanOrEqualTo(0);
        assertThat(sql.indexOf("ALTER TABLE `cut_task`")).isGreaterThan(preflight);
        assertThat(sql.indexOf("CREATE TABLE `cut_plan_revision`")).isGreaterThan(preflight);
        assertThat(sql).contains("DROP PROCEDURE IF EXISTS `fcut004_require_stage_contract`")
                .doesNotContain("UPDATE `cut_task`")
                .doesNotContain("UPDATE `cut_task_stage_history`");
    }

    @Test
    void createsTheThreeLockedCutTablesAndKeys() {
        assertThat(sql).contains("CREATE TABLE `cut_plan_revision`")
                .contains("CREATE TABLE `cut_step`")
                .contains("CREATE TABLE `cut_cutover_support_arrangement`")
                .contains("UNIQUE KEY `uk_cut_plan_revision_no`")
                .contains("UNIQUE KEY `uk_cut_plan_current`")
                .contains("UNIQUE KEY `uk_cut_plan_legacy`")
                .contains("UNIQUE KEY `uk_cut_step_order`")
                .contains("UNIQUE KEY `uk_cut_support_role`");
    }

    @Test
    void locksPlanLifecycleAndContentUnions() {
        assertThat(sql).contains("`chk_cut_stage_trigger` CHECK (\n    COALESCE((")
                .contains("`chk_cut_plan_derivation` CHECK (\n    COALESCE((")
                .contains("`chk_cut_plan_union` CHECK (\n    COALESCE((")
                .contains("`status_code` = 'DRAFT'")
                .contains("`status_code` = 'SUBMITTED'")
                .contains("`status_code` = 'INVALIDATED'")
                .contains("`edit_mode_code` = 'FULL_FILE_UPLOAD'")
                .contains("`edit_mode_code` = 'ONLINE_TEMPLATE_STANDARD'")
                .contains("`edit_mode_code` = 'ONLINE_TEMPLATE_SIMPLE_D'")
                .contains("`grade_code` IN ('A','B','C')")
                .contains("`grade_code` = 'D'")
                .contains("`file_sha256` REGEXP '^[0-9a-f]{64}$'");
    }

    @Test
    void forwardsEveryP4P5P6TaskTransition() {
        assertThat(sql).contains("'P4_PLAN_SUBMITTED'")
                .contains("'P5_SOURCE_INVALIDATED','P5_APPROVAL_REJECTED'")
                .contains("'P5_APPROVAL_APPROVED'")
                .contains("`current_stage` = 'P5' AND `task_status` = 'APPROVING'")
                .contains("`current_stage` = 'P6' AND `task_status` = 'CLOSURE_IN_PROGRESS'");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V150__fcut004_p4_cutover_plan.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
