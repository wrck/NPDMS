package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FCut003MigrationContractTest {

    private final String sql = readMigration();

    @Test
    void freezesTaskConfigurationBeforeCreatingChecklistTables() {
        int preflight = sql.indexOf("CALL `fcut003_require_unique_task_configuration`()");
        int taskAlter = sql.indexOf("ALTER TABLE `cut_task`");
        int checklistCreate = sql.indexOf("CREATE TABLE `cut_cutover_checklist`");

        assertThat(preflight).isGreaterThanOrEqualTo(0);
        assertThat(taskAlter).isGreaterThan(preflight);
        assertThat(checklistCreate).isGreaterThan(taskAlter);
        assertThat(sql).contains("`status_code` IN ('PUBLISHED', 'DISABLED')")
                .contains("`task_origin` = 'LEGACY_FORWARD'")
                .contains("`configuration_revision_id` IS NULL")
                .doesNotContain("CUTOVER_DEFAULT");
    }

    @Test
    void createsOnlyThreeCutChecklistTablesAndLockedPermissions() {
        assertThat(count("CREATE TABLE `cut_cutover_checklist`")).isEqualTo(1);
        assertThat(count("CREATE TABLE `cut_cutover_checklist_item`")).isEqualTo(1);
        assertThat(count("CREATE TABLE `cut_cutover_checklist_item_result`")).isEqualTo(1);
        assertThat(sql).contains("pms:cutover-task:save-checklist")
                .contains("pms:cutover-task:request-collection")
                .contains("pms:cutover-task:submit-checklist")
                .doesNotContain("system_role_menu");
    }

    private long count(String token) {
        return sql.lines().filter(line -> line.contains(token)).count();
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V147__fcut003_p3_dynamic_checklist.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
