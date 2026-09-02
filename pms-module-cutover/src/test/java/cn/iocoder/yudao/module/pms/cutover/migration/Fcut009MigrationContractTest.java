package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut009MigrationContractTest {

    private final String sql = readMigration();

    @Test
    void addsOnlyTheNullableStrictNavigationSnapshot() {
        assertThat(sql)
                .contains("ADD COLUMN `navigation_rule_snapshot` json NULL")
                .contains("JSON_LENGTH(`navigation_rule_snapshot`) = 1")
                .contains("JSON_CONTAINS_PATH(`navigation_rule_snapshot`, 'one', '$.target') = 1")
                .contains("'CURRENT_STAGE_WORKBENCH', 'TASK_OVERVIEW'")
                .doesNotContain("UPDATE `cut_cutover_configuration_revision`")
                .doesNotContain("DEFAULT")
                .doesNotContain("CREATE TABLE");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V160__fcut009_navigation_rule.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
