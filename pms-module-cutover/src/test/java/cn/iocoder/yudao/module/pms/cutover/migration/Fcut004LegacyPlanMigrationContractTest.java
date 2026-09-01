package cn.iocoder.yudao.module.pms.cutover.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Fcut004LegacyPlanMigrationContractTest {

    @Test
    void registersLegacyPlanJobPausedWithoutQuartzActivation() throws Exception {
        String sql = Files.readString(Path.of("../sql/migrations/V151__fcut004_legacy_plan_job.sql"));

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
                .doesNotContain("FROM pms_cut_plan", "JOIN pms_cut_plan");
    }
}
