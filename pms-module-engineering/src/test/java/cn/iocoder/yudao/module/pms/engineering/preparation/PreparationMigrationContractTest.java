package cn.iocoder.yudao.module.pms.engineering.preparation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparationMigrationContractTest {

    private static String schemaSql;
    private static String seedSql;
    private static String errorCodes;

    @BeforeAll
    static void loadSources() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        Path migrationDirectory = repositoryDirectory.resolve("sql/migrations");
        schemaSql = Files.readString(
                migrationDirectory.resolve("V96__fsol002_preparation_readiness.sql"), StandardCharsets.UTF_8);
        seedSql = Files.readString(
                migrationDirectory.resolve("V97__fsol002_preparation_seed.sql"), StandardCharsets.UTF_8);
        errorCodes = Files.readString(repositoryDirectory.resolve(
                "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/enums/ErrorCodeConstants.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void definesExactlyTheSixApprovedSolTables() {
        assertEquals(6, occurrences(schemaSql, "CREATE TABLE `sol_"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_preparation`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_preparation_item`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_dynamic_form_instance`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_preparation_source_reference`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_preparation_item_waiver`"));
        assertTrue(schemaSql.contains("CREATE TABLE `sol_preparation_readiness_snapshot`"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
        assertFalse(schemaSql.contains("REFERENCES `plt_"));
        assertFalse(schemaSql.contains("REFERENCES `oa_"));
    }

    @Test
    void keepsCurrentHistoryAndAllInternalReferencesTenantScoped() {
        assertTrue(schemaSql.contains("(`tenant_id`, `project_id`, `preparation_type_code`, `business_version`)"));
        assertTrue(schemaSql.contains("(`tenant_id`, `project_id`, `preparation_type_code`, `current_marker`)"));
        assertTrue(schemaSql.contains("CHECK (`current_marker` IS NULL OR `current_marker` = 1)"));
        assertEquals(10, occurrences(schemaSql, "FOREIGN KEY (`tenant_id`,"));
        assertTrue(schemaSql.contains("REFERENCES `sol_preparation_readiness_snapshot` (`tenant_id`, `id`)"));
    }

    @Test
    void constrainsIndependentStateAxesAndImmutableSnapshotShape() {
        assertTrue(schemaSql.contains("'DRAFT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'RETURNED'"));
        assertTrue(schemaSql.contains("'REQUIRED', 'NOT_APPLICABLE_PENDING', 'NOT_APPLICABLE_CONFIRMED'"));
        assertTrue(schemaSql.contains("'SYNCED', 'ERROR', 'UNKNOWN'"));
        assertTrue(schemaSql.contains("'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN'"));
        assertTrue(schemaSql.contains("'NOT_READY', 'READY'"));
        assertFalse(schemaSql.substring(schemaSql.indexOf("CREATE TABLE `sol_preparation_readiness_snapshot`"))
                .contains("`updater`"));
    }

    @Test
    void seedsFixedCatalogApprovedItemsAndOnlyDraftTemplateExamples() {
        assertTrue(seedSql.contains("pms_preparation_survey_item_code"));
        for (String item : new String[]{"POWER", "NETWORK_PORT", "FIBER", "CABINET",
                "NETWORK_CABLE", "OPTICAL_MODULE"}) {
            assertTrue(seedSql.contains("\"formCode\":\"" + item + "\""));
        }
        assertTrue(seedSql.contains("pms.sol.preparation.site-survey.form-catalog.v1"));
        assertTrue(seedSql.contains("\"catalogVersion\":1"));
        assertTrue(seedSql.contains("\"commonFields\""));
        assertTrue(seedSql.contains("\"sourceRequirementCode\":\"OA_REQUIRED\""));
        assertTrue(seedSql.contains("\"enabled\":false"));
        assertTrue(seedSql.contains("WHERE r.`status` = 'DRAFT'"));
        assertTrue(seedSql.contains("r.`creator` = 'seed'"));
        assertFalse(seedSql.contains("INSERT INTO `system_role_menu`"));
    }

    @Test
    void seedsOnlyTheFourStablePermissionsAndPreparationErrorRange() {
        assertTrue(seedSql.contains("pms:preparation-survey:query"));
        assertTrue(seedSql.contains("pms:preparation-survey:manage"));
        assertTrue(seedSql.contains("pms:preparation-survey:fill"));
        assertTrue(seedSql.contains("pms:preparation-survey:waiver-approve"));
        assertEquals(14, occurrences(errorCodes, "new ErrorCode(1_011_024_"));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

}
