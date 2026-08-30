package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.DeviceCurrentProductTypeDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetProductTypeSchemaContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws Exception {
        Path migration = locateRepositoryRoot().resolve(
                "sql/migrations/V132__fast002_asset_product_type.sql");
        assertTrue(Files.exists(migration));
        sql = Files.readString(migration, StandardCharsets.UTF_8);
    }

    @Test
    void createsOnlyThreeAstProductTypeTablesWithoutSeedsOrDestructiveDdl() {
        Matcher tables = Pattern.compile("CREATE TABLE `ast_(?:product_type|product_type_source_mapping|device_current_product_type)`")
                .matcher(sql);
        int tableCount = 0;
        while (tables.find()) {
            tableCount++;
        }
        assertEquals(3, tableCount);
        assertFalse(Pattern.compile("(?im)^\\s*(DROP|TRUNCATE|DELETE|INSERT|UPDATE)\\b").matcher(sql).find());
        assertFalse(sql.contains("conp_type"));
        assertFalse(sql.contains("pms_product_type"));
        assertFalse(Pattern.compile("(?i)ALTER\\s+TABLE\\s+`?ast_device`?").matcher(sql).find());
    }

    @Test
    void definesTechnicalIdsStableKeysAndTenantLocalReferences() throws Exception {
        assertEquals(3, countMatches(sql, "`id` bigint NOT NULL AUTO_INCREMENT"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_tenant_code` (`tenant_id`, `type_code`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_tenant_id` (`tenant_id`, `id`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_tenant_id_code` (`tenant_id`, `id`, `type_code`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_mapping_source` (`tenant_id`, `source_system`, `source_key`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_mapping_tenant_id` (`tenant_id`, `id`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_product_type_mapping_tenant_id_target` (`tenant_id`, `id`, `product_type_id`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_device_product_type_current` (`tenant_id`, `device_id`, `current_marker`)"));
        assertTrue(sql.contains("FOREIGN KEY (`tenant_id`, `product_type_id`) REFERENCES `ast_product_type` (`tenant_id`, `id`)"));
        assertTrue(sql.contains("FOREIGN KEY (`tenant_id`, `product_type_id`, `product_type_code`)\n    REFERENCES `ast_product_type` (`tenant_id`, `id`, `type_code`)"));
        assertTrue(sql.contains("FOREIGN KEY (`tenant_id`, `source_mapping_id`)\n    REFERENCES `ast_product_type_source_mapping` (`tenant_id`, `id`)"));
        assertTrue(sql.contains("FOREIGN KEY (`tenant_id`, `source_mapping_id`, `product_type_id`)\n    REFERENCES `ast_product_type_source_mapping` (`tenant_id`, `id`, `product_type_id`)"));
        assertFalse(sql.contains("REFERENCES `ast_device`"));
        Path deviceMigration = locateRepositoryRoot().resolve(
                "sql/migrations/V109__fast001_device_master_and_source_facts.sql");
        String deviceSql = Files.readString(deviceMigration, StandardCharsets.UTF_8);
        assertFalse(deviceSql.contains("UNIQUE KEY `uk_ast_device_tenant_id` (`tenant_id`, `id`)"));
    }

    @Test
    void constrainsSourceWatermarkMappingAndCurrentResolution() {
        assertEquals(3, countMatches(sql, "`source_updated_at` datetime(3) NOT NULL"));
        assertEquals(2, countMatches(sql, "`synced_at` datetime(3)"));
        assertFalse(sql.contains("last_successful_sync_time"));
        assertTrue(sql.contains("CHECK (`sync_status` IN ('FRESH', 'STALE', 'FAILED', 'PENDING_MAPPING', 'NOT_AVAILABLE'))"));
        assertTrue(sql.contains("CHECK (`mapping_status` IN ('RESOLVED', 'CONFLICT', 'UNRESOLVED'))"));
        assertTrue(sql.contains("CHECK (`resolution_status` IN ('RESOLVED', 'UNKNOWN', 'CONFLICT', 'UNRESOLVED'))"));
        assertTrue(sql.contains("`mapping_status` = 'RESOLVED' AND `product_type_id` IS NOT NULL"));
        assertTrue(sql.contains("`mapping_status` = 'UNRESOLVED' AND `product_type_id` IS NULL"));
        assertTrue(sql.contains("CONSTRAINT `chk_ast_product_type_mapping_conflict_evidence`"));
        assertTrue(sql.contains("`mapping_status` = 'CONFLICT'"));
        assertTrue(sql.contains("`conflict_product_type_code` IS NOT NULL"));
        assertTrue(sql.contains("`conflict_source_version` IS NOT NULL"));
        assertTrue(sql.contains("`conflict_source_updated_at` IS NOT NULL"));
        assertTrue(sql.contains("`conflict_payload_hash` IS NOT NULL"));
        assertTrue(sql.contains("`mapping_status` <> 'CONFLICT'"));
        assertTrue(sql.contains("`conflict_product_type_code` IS NULL"));
        assertTrue(sql.contains("`conflict_source_version` IS NULL"));
        assertTrue(sql.contains("`conflict_source_updated_at` IS NULL"));
        assertTrue(sql.contains("`conflict_payload_hash` IS NULL"));
        assertTrue(sql.contains("`current_marker` tinyint GENERATED ALWAYS AS"));
        assertTrue(sql.contains("WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN 1"));
        assertTrue(sql.contains("`effective_to` IS NULL OR `effective_to` >= `effective_from`"));
        assertTrue(sql.contains("`resolution_status` = 'RESOLVED'\n      AND `product_type_id` IS NOT NULL\n      AND `product_type_code` IS NOT NULL\n      AND `source_mapping_id` IS NOT NULL"));
        assertTrue(sql.contains("`resolution_status` <> 'RESOLVED'\n        AND `product_type_id` IS NULL\n        AND `product_type_code` IS NULL"));
    }

    @Test
    void mapsTenantDataObjectsAndGeneratedMarker() throws Exception {
        assertDataObject(AssetProductTypeDO.class, "ast_product_type", Set.of(
                "id", "typeCode", "displayName", "enabled", "sourceSystem", "sourceKey",
                "sourceVersion", "sourceUpdatedAt", "payloadHash", "syncStatus",
                "lastSyncAttemptAt", "syncedAt", "version"));
        assertDataObject(AssetProductTypeSourceMappingDO.class, "ast_product_type_source_mapping", Set.of(
                "id", "sourceSystem", "sourceKey", "sourceVersion", "sourceUpdatedAt",
                "payloadHash", "productTypeId", "mappingStatus", "conflictProductTypeCode",
                "conflictSourceVersion", "conflictSourceUpdatedAt", "conflictPayloadHash",
                "syncedAt", "version"));
        assertDataObject(DeviceCurrentProductTypeDO.class, "ast_device_current_product_type", Set.of(
                "id", "deviceId", "productTypeId", "productTypeCode", "sourceMappingId",
                "resolutionStatus", "sourceVersion", "sourceUpdatedAt", "effectiveFrom",
                "effectiveTo", "currentMarker", "version"));
        TableField currentMarker = DeviceCurrentProductTypeDO.class.getDeclaredField("currentMarker")
                .getAnnotation(TableField.class);
        assertNotNull(currentMarker);
        assertEquals(FieldStrategy.NEVER, currentMarker.insertStrategy());
        assertEquals(FieldStrategy.NEVER, currentMarker.updateStrategy());
    }

    private static void assertDataObject(Class<?> type, String tableName, Set<String> expectedFields) throws Exception {
        assertTrue(TenantBaseDO.class.isAssignableFrom(type));
        TableName table = type.getAnnotation(TableName.class);
        assertNotNull(table);
        assertEquals(tableName, table.value());
        assertEquals(expectedFields, java.util.Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .collect(java.util.stream.Collectors.toSet()));
        String tableSql = tableDefinition(tableName);
        expectedFields.forEach(field -> assertTrue(tableSql.contains("`" + toSnakeCase(field) + "`")));
        assertNotNull(type.getDeclaredField("id").getAnnotation(TableId.class));
        assertNotNull(type.getDeclaredField("version").getAnnotation(Version.class));
        assertFalse(hasDeclaredField(type, "tenantId"));
        assertFalse(hasDeclaredField(type, "deleted"));
    }

    private static boolean hasDeclaredField(Class<?> type, String fieldName) {
        try {
            type.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private static String tableDefinition(String tableName) {
        Pattern pattern = Pattern.compile(
                "CREATE TABLE `" + Pattern.quote(tableName) + "` \\((.*?)\\n\\) ENGINE=InnoDB",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static int countMatches(String value, String token) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(token, offset)) >= 0; offset += token.length()) {
            count++;
        }
        return count;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/migrations"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位仓库根目录");
        }
        return current;
    }
}
