package cn.iocoder.yudao.module.pms.project.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Facc002MigrationContractTest {

    @Test
    void v171MustCreateOrRevalidateOneCompleteManagedState() throws Exception {
        String sql = readMigration("V171__facc002_satisfaction_questionnaire_result_forward.sql");

        assertTrue(sql.contains("target_table_count=0 AND target_task_column_count=0"));
        assertTrue(sql.contains("target_table_count=12 AND exact_table_shape_count=12"));
        assertTrue(sql.contains("exact_unique_index_count=17 AND exact_check_count=5"));
        assertTrue(sql.contains("partial or conflicting"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `acc_satisfaction_collection_task`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `acc_satisfaction_result`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `plt_export_task`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `plt_export_audit`"));
        assertTrue(sql.contains("@facc002_v171_apply=1"));
        assertTrue(sql.contains("involved_root_count=4 AND exact_root_count=4"));
        assertTrue(sql.contains("FACC002-SEED-EXACT"));
        assertTrue(sql.contains("FACC002-SEED-AMB-A"));
        assertTrue(sql.contains("FACC002-SEED-AMB-B"));
        assertTrue(sql.contains("FACC002-SEED-DISABLED"));
    }

    @Test
    void v171MustFreezePermissionsAndAllFiveRuntimeJobs() throws Exception {
        String sql = readMigration("V171__facc002_satisfaction_questionnaire_result_forward.sql");

        for (String menuId : new String[]{"930930", "930931", "930932", "930933", "930934", "930935"}) {
            assertTrue(sql.contains(menuId));
        }
        for (String permission : new String[]{"pms:acceptance:satisfaction:query",
                "pms:acceptance:satisfaction:manage", "pms:acceptance:satisfaction:collect",
                "pms:acceptance:satisfaction:export", "pms:acceptance:satisfaction:download"}) {
            assertTrue(sql.contains(permission));
        }
        for (String handler : new String[]{"satisfactionTaskOutboxDeliveryJob",
                "satisfactionResultOutboxDeliveryJob", "satisfactionResultArchiveCompensationJob",
                "exportTaskExecutionJob", "exportFileExpirationJob"}) {
            assertTrue(sql.contains(handler));
        }
        assertTrue(sql.contains("involved_menu_count=6 AND exact_menu_count=6"));
        assertTrue(sql.contains("involved_grant_count=6 AND exact_grant_count=6"));
        assertTrue(sql.contains("involved_job_count=5 AND exact_job_count=5"));
    }

    private static String readMigration(String filename) throws Exception {
        return Files.readString(Path.of("..", "sql", "migrations", filename))
                .replace("\r\n", "\n");
    }
}
