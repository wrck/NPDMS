package cn.iocoder.yudao.module.pms.engineering.constructionplan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionPlanMigrationContractTest {

    private static String schemaSql;
    private static String seedSql;
    private static String fileFreezeSql;

    @BeforeAll
    static void loadMigrations() throws IOException {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path migrationDirectory = workingDirectory.resolve("sql/migrations");
        if (!Files.exists(migrationDirectory)) {
            migrationDirectory = workingDirectory.resolve("../sql/migrations").normalize();
        }
        schemaSql = Files.readString(
                migrationDirectory.resolve("V90__fsol001_construction_plan_duration.sql"), StandardCharsets.UTF_8);
        seedSql = Files.readString(
                migrationDirectory.resolve("V91__fsol001_duration_seed.sql"), StandardCharsets.UTF_8);
        fileFreezeSql = Files.readString(
                migrationDirectory.resolve("V95__fsol001_file_artifact_freeze.sql"), StandardCharsets.UTF_8);
    }

    @Test
    void definesOnlyTheThreeApprovedSolTables() {
        assertTrue(schemaSql.contains("CREATE TABLE `sol_construction_plan`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_construction_plan_revision`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_construction_plan_change`"));
        assertFalse(schemaSql.contains("sol_construction_plan_item"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
    }

    @Test
    void keepsRootAndInternalReferencesTenantScoped() {
        assertTrue(schemaSql.contains(
                "UNIQUE KEY `uk_sol_construction_plan_project` (`tenant_id`, `project_id`)"));
        assertFalse(schemaSql.contains("(`tenant_id`, `project_id`, `deleted`)"));
        assertTrue(schemaSql.contains(
                "FOREIGN KEY (`tenant_id`, `plan_id`)\n        REFERENCES `sol_construction_plan` (`tenant_id`, `id`)"));
        assertTrue(schemaSql.contains(
                "FOREIGN KEY (`tenant_id`, `candidate_revision_id`)\n        REFERENCES `sol_construction_plan_revision` (`tenant_id`, `id`)"));
        assertTrue(schemaSql.contains("`current_duration_revision_id` BIGINT NULL"));
        assertTrue(schemaSql.contains("`plan_recalculation_source_revision_id` BIGINT NULL"));
    }

    @Test
    void constrainsDurationAndTheThreeIndependentStateAxes() {
        assertTrue(schemaSql.contains("DATEDIFF(`end_date`, `start_date`) + 1 = `duration_days`"));
        assertTrue(schemaSql.contains("'DATE_RANGE', 'DURATION_FROM_START'"));
        assertTrue(schemaSql.contains("'PENDING_RECALCULATION', 'RECALCULATED', 'RECALCULATION_FAILED'"));
        assertTrue(schemaSql.contains("'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN'"));
        assertTrue(schemaSql.contains("`current_duration_revision_id`"));
        assertFalse(schemaSql.contains("current_marker"));
    }

    @Test
    void seedsApprovedConfigurationAndRetiresOnlyLegacyWrites() {
        assertTrue(seedSql.contains("pms_duration_change_reason_type"));
        assertTrue(seedSql.contains("CUSTOMER_DELAY"));
        assertTrue(seedSql.contains("pms.sol.duration-change.customer-evidence-required-reason-codes"));
        assertTrue(seedSql.contains("pms:construction-plan:query"));
        assertTrue(seedSql.contains("pms:construction-plan:duration-manage"));
        assertTrue(seedSql.contains("pms:construction-plan:duration-approve"));
        assertTrue(seedSql.contains("19146, 19147, 19148, 19149, 19150"));
        assertTrue(seedSql.contains("19152, 19153, 19154, 19155, 19156"));
        assertFalse(seedSql.contains("INSERT INTO `system_role_menu`"));
        assertFalse(seedSql.contains("DELETE FROM"));
    }

    @Test
    void addsOnlyNullableFrozenFileFactsForward() {
        assertTrue(fileFreezeSql.contains("ALTER TABLE `sol_construction_plan_change`"));
        assertTrue(fileFreezeSql.contains("`customer_evidence_reference_key` VARCHAR(128) NULL"));
        assertTrue(fileFreezeSql.contains("`customer_evidence_artifact_version` INT UNSIGNED NULL"));
        assertTrue(fileFreezeSql.contains("`customer_evidence_reference_version` INT UNSIGNED NULL"));
        assertTrue(fileFreezeSql.contains("`customer_evidence_availability_version` INT UNSIGNED NULL"));
        assertTrue(fileFreezeSql.contains("`customer_evidence_scope_version` BIGINT NULL"));
        assertFalse(fileFreezeSql.contains("CREATE TABLE"));
        assertFalse(fileFreezeSql.contains("proj_"));
    }

}
