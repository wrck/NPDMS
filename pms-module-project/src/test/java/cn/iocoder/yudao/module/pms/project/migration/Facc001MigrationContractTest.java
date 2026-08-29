package cn.iocoder.yudao.module.pms.project.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Facc001MigrationContractTest {

    @Test
    void v128MustKeepManagedPairAndLegacyPartitionRules() throws Exception {
        String sql = Files.readString(Path.of("..", "sql", "migrations",
                "V128__facc001_acceptance_report_version_forward.sql"));

        assertTrue(sql.contains("terminal_count=2 OR nonterminal_count=2"));
        assertTrue(sql.contains("work_binding_type_code`='TASK_NATIVE'"));
        assertTrue(sql.contains("_facc001_conversion_pair"));
        assertTrue(sql.contains("992004500001"));
        assertTrue(sql.contains("992004500002"));
        assertTrue(sql.contains("992004400003"));
        assertTrue(sql.contains("992004400004"));
        assertTrue(sql.contains("activity_status`='PENDING'"));
        assertTrue(sql.contains("acc_acceptance_report_version"));
        assertTrue(sql.contains("pms:acceptance:report:complete"));
        assertTrue(sql.contains("pms:project-task:execute"));
        assertFalse(sql.contains("UPDATE `pms_acc_acceptance`"));
        assertFalse(sql.contains("UPDATE `proj_project_task` SET `status`"));
    }
}
