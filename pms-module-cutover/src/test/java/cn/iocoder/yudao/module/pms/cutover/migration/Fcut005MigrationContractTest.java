package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut005MigrationContractTest {
    private final String sql = readMigration();

    @Test
    void createsOnlyTheFiveLockedApprovalTables() {
        assertThat(sql).contains("CREATE TABLE `cut_approval_instance`")
                .contains("CREATE TABLE `cut_approval_node`")
                .contains("CREATE TABLE `cut_approval_review_item`")
                .contains("CREATE TABLE `cut_approval_reassignment`")
                .contains("CREATE TABLE `cut_approval_notification`")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("UPDATE `cut_task`")
                .doesNotContain("pms_cut_");
    }

    @Test
    void locksApprovalIdentityPendingNodeAndDeliveryKey() {
        assertThat(sql).contains("UNIQUE KEY `uk_cut_approval_task_plan`")
                .contains("UNIQUE KEY `uk_cut_approval_previous`")
                .contains("UNIQUE KEY `uk_cut_approval_pending_node`")
                .contains("GENERATED ALWAYS AS")
                .contains("CASE WHEN `status_code` = 'PENDING' THEN 1 ELSE NULL END")
                .contains("UNIQUE KEY `uk_cut_approval_notification_delivery`");
    }

    @Test
    void locksTheFiveReviewItemsAndStateUnions() {
        assertThat(sql).contains("'PREPARATION','BUSINESS_TEST','EXECUTION','ROLLBACK','OTHER'")
                .contains("'PENDING','PAUSED_SOURCE_INVALIDATED','APPROVED','REJECTED'")
                .contains("'WAITING','PENDING','APPROVED','REJECTED','CANCELLED'")
                .contains("'ROUTE_CANDIDATE_NOT_UNIQUE','APPROVER_UNAVAILABLE'")
                .contains("'PENDING_RETRY'")
                .contains("`decision_code` = 'NO'")
                .contains("`grade_code` IN ('A','B','C')")
                .contains("`grade_code` = 'D'");
    }

    private static String readMigration() {
        try {
            return Files.readString(Path.of("../sql/migrations/V153__fcut005_p5_graded_approval.sql"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
