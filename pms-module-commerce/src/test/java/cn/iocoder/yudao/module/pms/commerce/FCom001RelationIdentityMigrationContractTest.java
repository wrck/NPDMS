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

class FCom001RelationIdentityMigrationContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("sql/migrations"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        sql = Files.readString(repositoryDirectory.resolve(
                        "sql/migrations/V145__fcom001_order_contract_relation_source_identity.sql"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    void failsClosedBeforeAnyAlterAndRemainsRepairable() {
        int guard = sql.indexOf("IF EXISTS (SELECT 1 FROM `com_sales_order_contract_relation` LIMIT 1)");
        int signal = sql.indexOf("SIGNAL SQLSTATE '45000'");
        int alter = sql.indexOf("ALTER TABLE `com_sales_order_contract_relation`");
        assertTrue(guard >= 0);
        assertTrue(signal > guard);
        assertTrue(alter > signal);
        assertEquals(2, occurrences(sql,
                "DROP PROCEDURE IF EXISTS `fcom001_require_empty_order_contract_relation`"));
        assertFalse(sql.contains("UPDATE `com_sales_order_contract_relation`"));
    }

    @Test
    void replacesSyntheticSingleKeyWithVerbatimSourcePair() {
        assertTrue(sql.contains("ADD COLUMN `sales_order_source_key` varchar(128) NOT NULL"));
        assertTrue(sql.contains("ADD COLUMN `contract_source_key` varchar(128) NOT NULL"));
        assertTrue(sql.contains("DROP COLUMN `source_key`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_com_order_contract_source_pair` "
                + "(`tenant_id`, `source_system`, `sales_order_source_key`, `contract_source_key`)"));
        assertFalse(sql.toLowerCase().contains("sha"));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int index = value.indexOf(token); index >= 0; index = value.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
