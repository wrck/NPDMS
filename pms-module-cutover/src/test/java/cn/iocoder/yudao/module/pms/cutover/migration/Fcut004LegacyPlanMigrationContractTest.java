package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut004LegacyPlanMigrationContractTest {

    @Test
    void registersLegacyPlanJobPausedWithoutQuartzActivation() throws Exception {
        String sql = Files.readString(Path.of("../sql/migrations/V182__fcut004_legacy_plan_job.sql"));

        assertThat(sql).contains("'legacyCutoverPlanReconciliationJob'")
                .contains("'割接方案旧数据核对', 2")
                .contains("SET `name`='割接方案旧数据核对', `status`=2")
                .doesNotContain("status`=1");
    }

    @Test
    void productionMapperNeverReadsLegacyPlanTable() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/resources/mapper/planv2/LegacyCutoverPlanReconciliationMapper.xml"));

        assertThat(mapper).contains("FROM cut_task", "FROM cut_plan_revision")
                .contains("legacy_task_id = #{query.legacyTaskId}")
                .contains("legacy_mapping_version = 'F-CUT-002-PMS-CUT-TASK-V1'")
                .doesNotContain("FROM pms_cut_plan", "JOIN pms_cut_plan");
    }
}
