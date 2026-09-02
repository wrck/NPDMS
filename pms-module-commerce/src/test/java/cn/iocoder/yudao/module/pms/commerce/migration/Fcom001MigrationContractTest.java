package cn.iocoder.yudao.module.pms.commerce.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fcom001MigrationContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        Path root = locateRepositoryRoot();
        sql = Files.readString(root.resolve("sql/migrations/V160__fcom001_contract_order_scope_forward_migration.sql"),
                StandardCharsets.UTF_8);
    }

    @Test
    void shouldCreateAllEightFixedShadowTables() {
        for (String table : List.of("com_contract", "com_sales_order", "com_sales_order_line",
                "com_order_contract_relation", "com_project_contract_relation", "com_delivery_scope",
                "com_delivery_scope_detail", "acc_acceptance_scope_binding")) {
            assertTrue(sql.contains("`fcom001_shadow_" + table + "`"), table);
        }
    }

    @Test
    void shouldPublishOnlyThroughOneMultiTableRename() {
        Matcher rename = Pattern.compile("(?is)\\bRENAME\\s+TABLE\\b").matcher(sql);
        int count = 0;
        while (rename.find()) {
            count++;
        }
        assertEquals(1, count);
        assertTrue(sql.contains("`com_order_line` TO `fcom001_v70_com_order_line`"));
        assertTrue(sql.contains("`com_delivery_scope` TO `fcom001_v70_com_delivery_scope`"));
        assertTrue(sql.contains("`com_delivery_scope_detail` TO `fcom001_v70_com_delivery_scope_detail`"));
        assertTrue(sql.contains("`fcom001_shadow_com_delivery_scope` TO `com_delivery_scope`"));
        assertTrue(sql.contains("`fcom001_shadow_acc_acceptance_scope_binding` TO `acc_acceptance_scope_binding`"));
    }

    @Test
    void shouldFailClosedOnPartialSeedOrOrdinaryRows() {
        assertTrue(sql.contains("FCOM001_V72_SEED_PARTIAL_OR_TAMPERED"));
        assertTrue(sql.contains("FCOM001_NON_SEED_V70_OWNER_FACTS_UNAVAILABLE"));
        assertTrue(sql.contains("creator = 'seed'"));
        assertTrue(sql.contains("source_system = 'SEED'"));
        assertTrue(sql.contains("FPROJ002-V18"));
        assertFalse(sql.contains("item_code AS product_code"));
        assertFalse(sql.contains("DELETE FROM `com_order_line`"));
    }

    @Test
    void shouldFreezeAndReconcileBeforePublishing() {
        assertTrue(sql.contains("APPLICATION_WRITE_STOP_REQUIRED"));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertTrue(sql.contains("COUNT(*), MIN(id), MAX(id), COALESCE(MAX(version), 0), MAX(update_time)"));
        assertTrue(countOccurrences(sql, "MAX(update_time)") >= 6);
        assertTrue(sql.contains("FCOM001_RECONCILIATION_FAILED"));
        assertTrue(sql.contains("FCOM001_V70_WATERMARK_CHANGED"));
        assertTrue(sql.indexOf("FCOM001_RECONCILIATION_FAILED") < sql.indexOf("RENAME TABLE"));
    }

    @Test
    void shouldHaveDeterministicFreshRetryAndPublishedReplayStates() {
        assertTrue(sql.contains("FCOM001_STATE_FRESH_V123"));
        assertTrue(sql.contains("FCOM001_STATE_PUBLISHED_REPLAY"));
        assertTrue(sql.contains("FCOM001_STATE_PUBLISHED_REPLAY_INVALID"));
        assertTrue(sql.contains("LEFT JOIN `com_delivery_scope`"));
        assertTrue(sql.contains("FCOM001_STATE_MIXED_RESTORE_SNAPSHOT"));
        assertTrue(sql.contains("DROP TABLE IF EXISTS `fcom001_shadow_acc_acceptance_scope_binding`"));
        assertTrue(sql.contains("DROP TABLE IF EXISTS `fcom001_shadow_com_contract`"));
    }

    private static int countOccurrences(String value, String expected) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = value.indexOf(expected, fromIndex)) >= 0) {
            count++;
            fromIndex += expected.length();
        }
        return count;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/migrations"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
