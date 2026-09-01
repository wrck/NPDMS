package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FCut002PhysicalConstraintMigrationContractTest {

    @Test
    void addsForwardOriginAndAssessmentStatusUnions() throws IOException {
        String sql = Files.readString(Path.of(
                "../sql/migrations/V149__fcut002_task_origin_assessment_checks.sql"));

        assertThat(sql).contains("CALL `fcut002_require_valid_origin_and_assessment`()")
                .contains("ADD CONSTRAINT `chk_cut_task_origin_union`")
                .contains("`task_origin` = 'NEW_PLATFORM'")
                .contains("`task_origin` = 'LEGACY_FORWARD'")
                .contains("ADD CONSTRAINT `chk_cut_assessment_status_union`")
                .contains("`assessment_status` = 'DRAFT'")
                .contains("`assessment_status` = 'SUBMITTED'")
                .contains("`assessment_status` = 'INVALIDATED'");
    }
}
