package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FCutDeviceTypeSnapshotMigrationContractTest {

    @Test
    void addsOnlyTheApprovedCutSnapshotFields() {
        String sql = readMigration();

        assertThat(sql).contains("ADD COLUMN `device_type_code_snapshot` varchar(64) DEFAULT NULL")
                .contains("ADD COLUMN `device_type_source_version_snapshot` varchar(128) DEFAULT NULL")
                .doesNotContain("device_type_source_key")
                .doesNotContain("device_type_assignment_version")
                .doesNotContain("ALTER TABLE `ast_device`");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V148__fcut_device_product_type_snapshot.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
