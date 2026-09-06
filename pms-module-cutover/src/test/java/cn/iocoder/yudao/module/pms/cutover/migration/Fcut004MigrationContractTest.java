package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut004MigrationContractTest {
    private final String sql = readMigration();
    private final String seedSql = readSeedMigration();

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
        assertThat(sql).contains("ADD CONSTRAINT `chk_cut_stage_trigger` CHECK (\n    COALESCE((")
                .contains("'P5_APPROVAL_APPROVED' AND `from_stage` = 'P5'")
                .contains("AND `to_stage` = 'P6' AND `to_status` = 'CLOSURE_IN_PROGRESS')\n    ), FALSE) = TRUE\n  );")
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

    @Test
    void seedsOnlyTheLockedPlanDictionaries() {
        assertThat(seedSql)
                .contains("'pms_cutover_plan_revision_status'")
                .contains("'DRAFT'", "'SUBMITTED'", "'INVALIDATED'")
                .contains("'ONLINE_TEMPLATE_STANDARD'", "'ONLINE_TEMPLATE_SIMPLE_D'",
                        "'FULL_FILE_UPLOAD'", "'LEGACY_READ_ONLY'")
                .contains("'PRE_OPERATION'", "'OPERATION'", "'CLOSING_COLLECTION'",
                        "'POST_BUSINESS_TEST'", "'ROLLBACK'", "'POST_CUTOVER_SUPPORT'")
                .contains("'CUSTOMER'", "'DP_FIRST_LINE'", "'DP_SECOND_LINE'", "'DP_RND'")
                .contains("'INITIAL'", "'APPROVAL_REJECTED'", "'DUTY_CHANGED'", "'SOURCE_REPLACED'");
    }

    @Test
    void addsFourPlanPermissionsWithoutGrantingRolesOrChangingLegacyMenus() {
        assertThat(seedSql)
                .contains("'pms:cutover-task:query-plan'")
                .contains("'pms:cutover-task:save-plan'")
                .contains("'pms:cutover-task:download-plan'")
                .contains("'pms:cutover-task:submit-plan'")
                .contains("992602050001")
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("system_role_menu")
                .doesNotContain("pms:cut-plan:")
                .doesNotContain("UPDATE `system_menu`");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V181__fcut004_p4_cutover_plan.sql"))
                    .replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readSeedMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V183__fcut004_plan_seed.sql"))
                    .replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
