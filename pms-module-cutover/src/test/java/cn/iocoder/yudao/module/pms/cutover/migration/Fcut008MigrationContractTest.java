package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut008MigrationContractTest {

    private final String sql = readMigration();
    private final String correlationSql = readCorrelationMigration();
    private final String externalJobSql = readExternalJobMigration();

    @Test
    void addsAndBackfillsTheLockedForwardColumnsWithoutBusinessDefaults() {
        assertThat(sql)
                .contains("ADD COLUMN `lead_time_enabled` bit(1) NULL")
                .contains("ADD COLUMN `lead_time_snapshot` json NULL")
                .contains("SET `lead_time_enabled` = b'0'")
                .contains("MODIFY COLUMN `lead_time_enabled` bit(1) NOT NULL")
                .contains("ADD COLUMN `channel_code` varchar(24) NULL")
                .contains("SET `channel_code` = 'IN_PLATFORM'")
                .contains("MODIFY COLUMN `channel_code` varchar(24) NOT NULL")
                .doesNotContain("DEFAULT b'0'")
                .doesNotContain("DEFAULT 'IN_PLATFORM'")
                .doesNotContain("pms_cut_");
    }

    @Test
    void keepsHistoricalInstancesDisabledAndOnlyAllowsNewABSnapshots() {
        assertThat(sql)
                .contains("`lead_time_enabled` = b'0' AND `lead_time_snapshot` IS NULL")
                .contains("`lead_time_enabled` = b'1' AND `grade_code` IN ('A','B')")
                .contains("`lead_time_snapshot` IS NOT NULL");
    }

    @Test
    void separatesInPlatformAndExternalDeliveryStateUnions() {
        assertThat(sql)
                .contains("'IN_PLATFORM','SMS','EMAIL','DINGTALK'")
                .contains("`channel_code` = 'IN_PLATFORM'")
                .contains("`channel_code` IN ('SMS','EMAIL','DINGTALK')")
                .contains("`status_code` = 'ACCEPTED'")
                .contains("`status_code` = 'DELIVERY_UNKNOWN'")
                .contains("`status_code` = 'PENDING_RETRY' AND `retry_count` > 0")
                .contains("`provider_reference_id` IS NULL AND `next_retry_at` IS NOT NULL")
                .contains("`last_attempt_at` IS NOT NULL")
                .contains("DROP CHECK `chk_cut_approval_notification_status`");
    }

    @Test
    void locksCorrelationProvenanceWithoutFabricatingHistoricalValues() {
        assertThat(correlationSql)
                .contains("DROP PROCEDURE IF EXISTS `fcut008_require_no_external_notification_history`")
                .contains("`channel_code` IN ('SMS','EMAIL','DINGTALK')")
                .contains("ADD COLUMN `correlation_id` varchar(128) NULL")
                .contains("CHAR_LENGTH(`correlation_id`) BETWEEN 1 AND 128")
                .contains("CHAR_LENGTH(`correlation_id`) = CHAR_LENGTH(TRIM(`correlation_id`))")
                .doesNotContain("`deleted`")
                .doesNotContain("UPDATE `cut_approval_notification`")
                .doesNotContain("DEFAULT");
    }

    @Test
    void registersOnlyTheExternalDeliveryJobAsPaused() {
        assertThat(externalJobSql)
                .contains("992602073002")
                .contains("'cutoverExternalApprovalNotificationJob'")
                .contains("'0/30 * * * * ?'")
                .contains("'割接P5审批外部提醒投递', 2")
                .contains("`status`=2")
                .contains("WHERE NOT EXISTS")
                .doesNotContain("cutoverApprovalNotificationJob'")
                .doesNotContain("syncEnabledJobByHandlerName")
                .doesNotContain("INSERT INTO `cut_approval_");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V188__fcut008_p5_lead_time_notification.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readCorrelationMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V189__fcut008_notification_correlation_provenance.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readExternalJobMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V190__fcut008_external_notification_job_seed.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readExternalJobMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V159__fcut008_external_notification_job_seed.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
