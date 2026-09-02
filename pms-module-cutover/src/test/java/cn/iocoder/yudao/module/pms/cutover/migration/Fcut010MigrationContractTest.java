package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut010MigrationContractTest {
    private final String sql = readMigration();

    @Test
    void createsOnlyTheThreeCutOwnedTablesWithoutBackfill() {
        assertThat(sql)
                .contains("CREATE TABLE `cut_spare_application_reference`")
                .contains("CREATE TABLE `cut_spare_status_revision`")
                .contains("CREATE TABLE `cut_spare_manual_evidence`")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("UPDATE `cut_task`")
                .doesNotContain("INSERT INTO `cut_spare_")
                .doesNotContain("pms_cut_");
    }

    @Test
    void locksApplicationIdentityStatesAndHistoryPointers() {
        assertThat(sql)
                .contains("'REQUEST_PENDING','EXTERNAL_REFERENCED','RETRY_PENDING'")
                .contains("UNIQUE KEY `uk_cut_spare_platform_request`")
                .contains("UNIQUE KEY `uk_cut_spare_external_request`")
                .contains("UNIQUE KEY `uk_cut_spare_external_application`")
                .contains("`current_status_revision_id` bigint DEFAULT NULL")
                .contains("`retry_count` >= 0 AND `version` >= 0")
                .contains("`integration_status` <> 'REQUEST_PENDING' OR `external_application_no` IS NULL")
                .contains("`deleted`=b'0'");
    }

    @Test
    void locksAppendOnlyStatusAndExactFileFactAxes() {
        assertThat(sql)
                .contains("'INITIATE_RESPONSE','CALLBACK','REFRESH'")
                .contains("UNIQUE KEY `uk_cut_spare_status_version`")
                .contains("UNIQUE KEY `uk_cut_spare_status_event`")
                .contains("UNIQUE KEY `uk_cut_spare_status_current`")
                .contains("OCTET_LENGTH(CAST(`status_snapshot` AS CHAR CHARACTER SET utf8mb4)) <= 16384")
                .contains("JSON_LENGTH(`file_fact_version`)=3")
                .contains("'$.artifactVersion','$.referenceVersion','$.availabilityVersion'")
                .contains("UNIQUE KEY `uk_cut_spare_manual_file`");
    }

    @Test
    void seedsOnlyTheNewButtonUnderTheExistingWorkbench() {
        assertThat(sql)
                .contains("'pms:cutover-task:manage-spare'")
                .contains("992602050001")
                .doesNotContain("system_role_menu")
                .doesNotContain("INSERT INTO `system_dict")
                .doesNotContain("infra_job");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V192__fcut010_spare_system_coordination.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
