package cn.iocoder.yudao.module.pms.project.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Facc001MigrationContractTest {

    @Test
    void v166MustKeepManagedPairAndLegacyPartitionRules() throws Exception {
        String sql = readMigration("V166__facc001_acceptance_report_version_forward.sql");

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

    @Test
    void v167MustGrantCompleteManagedRoleMenuAncestorClosure() throws Exception {
        String sql = readMigration("V167__facc001_acceptance_role_menu_ancestor_fix.sql");

        assertTrue(sql.contains("992004800002"));
        for (String menuId : new String[]{"19260", "19266", "19261", "18000", "1243", "2"}) {
            assertTrue(sql.contains(menuId));
        }
        assertTrue(sql.contains("managed_ancestor_menu_count <> 6"));
        assertTrue(sql.contains("managed_ancestor_grant_count = 0"));
        assertTrue(sql.contains("managed_ancestor_grant_row_count = 0"));
        assertTrue(sql.contains("managed_ancestor_grant_count = 6"));
        assertFalse(sql.contains("INSERT INTO `system_menu`"));
        assertFalse(sql.contains("INSERT INTO `system_role`"));
    }

    @Test
    void v168MustOnlyRepairV166ActivitiesToTheirCurrentAccContracts() throws Exception {
        String sql = readMigration("V168__facc001_acceptance_activity_contract_identity_fix.sql");

        assertTrue(sql.contains("activity.`creator` = 'v166-facc001'"));
        assertTrue(sql.contains("current_contract.`target_context_code` = 'ACC'"));
        assertTrue(sql.contains("current_contract.`target_object_type` = 'AcceptanceActivity'"));
        assertTrue(sql.contains("CAST(current_contract.`target_object_key` AS BINARY) = CAST(activity.`id` AS BINARY)"));
        assertTrue(sql.contains("current_contract.`effective_to` IS NULL"));
        assertTrue(sql.contains("old_contract.`work_binding_type_code` = 'TASK_NATIVE'"));
        assertTrue(sql.contains("old_contract.`effective_to` IS NOT NULL"));
        assertTrue(sql.contains("mapping_row_count <> target_activity_count"));
        assertTrue(sql.contains("mapping_activity_count <> target_activity_count"));
        assertTrue(sql.contains("activity.`execution_contract_id` = mapping.`current_contract_id`"));
        assertFalse(sql.contains("UPDATE `proj_project_task`"));
        assertFalse(sql.contains("UPDATE `proj_project_task_execution_contract`"));
        assertFalse(sql.contains("UPDATE `acc_acceptance_report_version`"));
    }

    @Test
    void v169MustSeedBothAcceptanceReportJobsAsOneCompletePair() throws Exception {
        String sql = readMigration("V169__facc001_acceptance_report_jobs.sql");

        assertTrue(sql.contains("992004900001"));
        assertTrue(sql.contains("992004900002"));
        assertTrue(sql.contains("acceptanceReportOutboxDeliveryJob"));
        assertTrue(sql.contains("acceptanceReportArchiveCompensationJob"));
        assertTrue(sql.contains("'0/30 * * * * ?'"));
        assertTrue(sql.contains("involved_row_count = 0"));
        assertTrue(sql.contains("involved_row_count = 2 AND exact_row_count = 2"));
        assertTrue(sql.contains("partial or conflicting"));
    }

    @Test
    void v170MustGrantOnlyTheExistingProjectQueryMenuToTheManagedRole() throws Exception {
        String sql = readMigration("V170__facc001_acceptance_project_query_permission.sql");

        assertTrue(sql.contains("992004800002"));
        assertTrue(sql.contains("18067"));
        assertTrue(sql.contains("pms:project:query"));
        assertTrue(sql.contains("parent_id` = 19261"));
        assertTrue(sql.contains("involved_grant_count = 0"));
        assertFalse(sql.contains("INSERT INTO `system_menu`"));
        assertFalse(sql.contains("INSERT INTO `system_role`"));
    }

    private static String readMigration(String filename) throws Exception {
        return Files.readString(Path.of("..", "sql", "migrations", filename))
                .replace("\r\n", "\n");
    }
}
