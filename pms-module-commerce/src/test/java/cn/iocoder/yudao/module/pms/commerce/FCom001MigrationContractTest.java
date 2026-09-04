package cn.iocoder.yudao.module.pms.commerce;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FCom001MigrationContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        sql = Files.readString(repositoryDirectory.resolve(
                "sql/migrations/V217__received_fcom001_contract_order_scope_schema.sql"), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    @Test
    void createsSixOwnerAndSupportTablesWithoutLegacySourceReads() {
        assertEquals(6, occurrences(sql, "CREATE TABLE `com_"));
        assertTrue(sql.contains("CREATE TABLE `com_contract`"));
        assertTrue(sql.contains("CREATE TABLE `com_sales_order`"));
        assertTrue(sql.contains("CREATE TABLE `com_sales_order_contract_relation`"));
        assertTrue(sql.contains("CREATE TABLE `com_project_contract_relation`"));
        assertTrue(sql.contains("CREATE TABLE `com_authority_candidate`"));
        assertTrue(sql.contains("CREATE TABLE `com_delivery_scope_project_version`"));
        assertFalse(sql.contains("sms_ofst_contract_head_sap"));
        assertFalse(sql.contains("pm_order_data_from_erp"));
        assertFalse(sql.contains("pm_order_line_from_erp"));
        assertFalse(sql.contains("pm_project_product_line"));
        assertFalse(sql.contains("INSERT INTO"));
    }

    @Test
    void extendsV70RowsOnlyWithNullableFactsAndNoInventedReconciliationStatus() {
        assertTrue(sql.contains("ADD COLUMN `model_code` varchar(64) DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `source_lifecycle_status` varchar(32) DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `unit_code` varchar(32) DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN `location_resolution_status` varchar(16) DEFAULT NULL"));
        assertFalse(sql.contains("ADD COLUMN `authority_status`"));
        assertFalse(sql.contains("UPDATE `com_order_line`"));
        assertFalse(sql.contains("UPDATE `com_delivery_scope_detail`"));
        assertFalse(sql.contains("PENDING_RECONCILIATION")
                && sql.substring(sql.indexOf("ALTER TABLE `com_order_line`")).contains("PENDING_RECONCILIATION"));
    }

    @Test
    void guardsDuplicateCurrentRowsBeforeReplacingGeneratedMarker() {
        int cleanup = sql.indexOf("DROP PROCEDURE IF EXISTS `fcom001_preflight_scope_current`");
        int create = sql.indexOf("CREATE PROCEDURE `fcom001_preflight_scope_current`");
        int duplicateGuard = sql.indexOf("HAVING COUNT(*) > 1");
        int signal = sql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = sql.indexOf("ALTER TABLE `com_delivery_scope` DROP INDEX `uk_com_scope_current`");
        assertTrue(cleanup >= 0);
        assertTrue(create > cleanup);
        assertTrue(duplicateGuard > create);
        assertTrue(signal > duplicateGuard);
        assertTrue(alter > signal);
        assertEquals(2, occurrences(sql,
                "DROP PROCEDURE IF EXISTS `fcom001_preflight_scope_current`"));
        String preflightPredicate = sql.substring(sql.indexOf("FROM `com_delivery_scope`"), duplicateGuard);
        assertFalse(preflightPredicate.contains("`deleted`"));
        assertTrue(sql.contains("CASE WHEN `scope_status` IN ('ACTIVE', 'CONFLICT') "
                + "AND `effective_to` IS NULL THEN 1 ELSE NULL END"));
    }

    @Test
    void keepsEveryOwnerSourceVersionAtThePublicContractLength() {
        assertEquals(3, occurrences(sql, "`source_version` varchar(64) NOT NULL"));
        assertTrue(sql.contains("`matched_owner_source_version` varchar(64) DEFAULT NULL"));
        assertTrue(sql.contains("`candidate_version` varchar(128) NOT NULL"));
    }

    @Test
    void appliesQualifiedDetailChecksOnlyAfterUnitFactExists() {
        assertTrue(sql.contains("`unit_code` IS NULL OR ("));
        assertTrue(sql.contains("NULLIF(TRIM(`product_code`), '') IS NOT NULL "
                + "OR NULLIF(TRIM(`model_code`), '') IS NOT NULL"));
        assertTrue(sql.contains("`location_resolution_status` = 'RESOLVED'"));
        assertTrue(sql.contains("`location_resolution_status` = 'UNRESOLVED'"));
        assertTrue(sql.contains("`serial_no` IS NULL OR `allocated_qty` = 1"));
    }

    @Test
    void locksOwnerEnumsIdentityAndProjectWatermarkConstraints() {
        assertTrue(sql.contains("'PENDING_AUTHORITY', 'CONFIRMED'"));
        assertTrue(sql.contains("'ACTIVE', 'CANCELLED', 'RETURNED'"));
        assertTrue(sql.contains("'PENDING_RECONCILIATION', 'MATCHED', 'REJECTED'"));
        assertTrue(sql.contains("UNIQUE KEY `uk_com_contract_source` "
                + "(`tenant_id`, `source_system`, `source_key`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_com_scope_project_version` (`tenant_id`, `project_id`)"));
        assertTrue(sql.contains("CHECK (`scope_version` >= 0)"));
        assertTrue(sql.contains("CHECK (`payload_version` >= 0)"));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int index = value.indexOf(token); index >= 0; index = value.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
