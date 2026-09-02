package cn.iocoder.yudao.module.pms.asset.dal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetProductTypeImportPermissionMigrationTest {

    private static final Path MIGRATION = Path.of("..", "sql", "migrations",
            "V165__fast002_product_type_import_permission.sql");

    @Test
    void shouldRegisterDedicatedPermissionWithoutRoleGrant() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("pms:asset-product-type:controlled-import"));
        assertTrue(sql.contains("system_menu"));
        assertFalse(sql.contains("system_role_menu"));
        assertFalse(sql.contains("pms:equipment:update"));
        assertFalse(sql.contains("INSERT INTO `system_role`"));
    }
}
