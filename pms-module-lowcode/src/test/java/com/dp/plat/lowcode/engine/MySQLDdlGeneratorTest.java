package com.dp.plat.lowcode.engine;

import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL DDL 生成器单元测试。
 */
@DisplayName("MySQL DDL 生成器测试")
class MySQLDdlGeneratorTest {

    private MySQLDdlGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MySQLDdlGenerator();
    }

    @Test
    @DisplayName("生成 CREATE TABLE — 含主键和普通字段")
    void generateCreateTable_basic() {
        LowCodeEntity entity = LowCodeEntity.builder()
                .tableName("pms_lc_device").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField nameField = LowCodeField.builder()
                .name("device_name").fieldType("STRING").length(128).nullable(0).build();

        String sql = generator.generateCreateTable(entity, List.of(idField, nameField), List.of());

        assertTrue(sql.contains("CREATE TABLE `pms_lc_device`"));
        assertTrue(sql.contains("`id` BIGINT NOT NULL"));
        assertTrue(sql.contains("PRIMARY KEY (`id`)"));
        assertTrue(sql.contains("`device_name` VARCHAR(128) NOT NULL"));
    }

    @Test
    @DisplayName("生成 CREATE TABLE — 含 DECIMAL 和 DATETIME")
    void generateCreateTable_decimalAndDatetime() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_invoice").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField amountField = LowCodeField.builder()
                .name("amount").fieldType("DECIMAL").length(12).scale(2).nullable(1).build();
        LowCodeField dateField = LowCodeField.builder()
                .name("invoice_date").fieldType("DATE").nullable(1).build();

        String sql = generator.generateCreateTable(entity, List.of(idField, amountField, dateField), List.of());

        assertTrue(sql.contains("`amount` DECIMAL(12,2) NULL"));
        assertTrue(sql.contains("`invoice_date` DATE NULL"));
    }

    @Test
    @DisplayName("生成 CREATE TABLE — 含索引和唯一约束")
    void generateCreateTable_indexAndUnique() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_asset").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField snField = LowCodeField.builder()
                .name("serial_no").fieldType("STRING").length(64).indexed(1).uniqueFlag(1).nullable(0).build();

        String sql = generator.generateCreateTable(entity, List.of(idField, snField), List.of());

        assertTrue(sql.contains("UNIQUE KEY `uk_serial_no` (`serial_no`)"));
        assertTrue(sql.contains("KEY `idx_serial_no` (`serial_no`)"));
    }

    @Test
    @DisplayName("生成 ALTER TABLE ADD COLUMN")
    void generateAddColumn() {
        LowCodeField field = LowCodeField.builder()
                .name("remark").fieldType("STRING").length(256).nullable(1).build();

        String sql = generator.generateAddColumn("pms_lc_device", field);

        assertEquals("ALTER TABLE `pms_lc_device` ADD COLUMN `remark` VARCHAR(256) NULL", sql);
    }

    @Test
    @DisplayName("生成 ALTER TABLE DROP COLUMN")
    void generateDropColumn() {
        String sql = generator.generateDropColumn("pms_lc_device", "remark");
        assertEquals("ALTER TABLE `pms_lc_device` DROP COLUMN `remark`", sql);
    }

    @Test
    @DisplayName("生成 CREATE INDEX — 普通索引")
    void generateCreateIndex_normal() {
        String sql = generator.generateCreateIndex("pms_lc_device", "idx_status",
                List.of("status", "create_time"), false);
        assertTrue(sql.contains("CREATE INDEX `idx_status` ON `pms_lc_device`"));
        assertTrue(sql.contains("(`status`, `create_time`)"));
    }

    @Test
    @DisplayName("生成 CREATE INDEX — 唯一索引")
    void generateCreateIndex_unique() {
        String sql = generator.generateCreateIndex("pms_lc_device", "uk_sn",
                List.of("serial_no"), true);
        assertTrue(sql.contains("CREATE UNIQUE INDEX `uk_sn`"));
    }

    @Test
    @DisplayName("生成多对多中间表 — CASCADE 级联删除")
    void generateJunctionTable_cascade() {
        String sql = generator.generateJunctionTable("pms_lc_user_role",
                "pms_lc_user", "pms_lc_role", "user_id", "role_id", "CASCADE");

        assertTrue(sql.contains("CREATE TABLE `pms_lc_user_role`"));
        assertTrue(sql.contains("`user_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`role_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("FOREIGN KEY (`user_id`) REFERENCES `pms_lc_user`(`id`)"));
        assertTrue(sql.contains("ON DELETE CASCADE"));
        assertTrue(sql.contains("PRIMARY KEY (`user_id`, `role_id`)"));
    }

    @Test
    @DisplayName("生成 CREATE TABLE — 含外键关联 ONE_TO_MANY")
    void generateCreateTable_withForeignKey() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_task").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField projectIdField = LowCodeField.builder()
                .name("project_id").fieldType("LONG").nullable(0).build();

        LowCodeRelation relation = LowCodeRelation.builder()
                .fromEntityId(2L).toEntityId(1L)
                .relationType("MANY_TO_ONE")
                .fromFieldName("project_id")
                .onDelete("RESTRICT")
                .onUpdate("RESTRICT")
                .build();

        String sql = generator.generateCreateTable(entity, List.of(idField, projectIdField), List.of(relation));

        assertTrue(sql.contains("FOREIGN KEY (`project_id`) REFERENCES"));
        assertTrue(sql.contains("ON DELETE RESTRICT"));
    }

    @Test
    @DisplayName("生成 CREATE TABLE — 自关联")
    void generateCreateTable_selfReference() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_category").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField parentIdField = LowCodeField.builder()
                .name("parent_id").fieldType("LONG").nullable(1).build();

        LowCodeRelation relation = LowCodeRelation.builder()
                .fromEntityId(1L).toEntityId(1L)  // 自关联
                .relationType("MANY_TO_ONE")
                .fromFieldName("parent_id")
                .onDelete("SET_NULL")
                .onUpdate("RESTRICT")
                .build();

        String sql = generator.generateCreateTable(entity, List.of(idField, parentIdField), List.of(relation));

        assertTrue(sql.contains("FOREIGN KEY (`parent_id`) REFERENCES `pms_lc_category`(`id`)"));
        assertTrue(sql.contains("ON DELETE SET NULL"));
    }

    @Test
    @DisplayName("字段类型映射 — TEXT 类型")
    void fieldTypeMapping_text() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_article").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField contentField = LowCodeField.builder()
                .name("content").fieldType("TEXT").nullable(1).build();

        String sql = generator.generateCreateTable(entity, List.of(idField, contentField), List.of());

        assertTrue(sql.contains("`content` TEXT NULL"));
    }

    @Test
    @DisplayName("字段类型映射 — BOOLEAN 类型")
    void fieldTypeMapping_boolean() {
        LowCodeEntity entity = LowCodeEntity.builder().tableName("pms_lc_flag").build();
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();
        LowCodeField activeField = LowCodeField.builder()
                .name("is_active").fieldType("BOOLEAN").nullable(0).defaultValue("1").build();

        String sql = generator.generateCreateTable(entity, List.of(idField, activeField), List.of());

        assertTrue(sql.contains("`is_active` TINYINT(1) NOT NULL DEFAULT 1"));
    }

    @Test
    @DisplayName("DROP INDEX 语句生成")
    void testGenerateDropIndex() {
        String sql = generator.generateDropIndex("pms_lc_order", "idx_status");
        assertTrue(sql.contains("DROP INDEX"), "应包含 DROP INDEX");
        assertTrue(sql.contains("`idx_status`"), "应包含索引名");
        assertTrue(sql.contains("`pms_lc_order`"), "应包含表名");
    }

    @Test
    @DisplayName("ALTER COLUMN 语句生成 — 修改列类型")
    void testGenerateAlterColumn() {
        LowCodeField field = LowCodeField.builder()
                .name("remark")
                .fieldType("STRING")
                .length(500)
                .nullable(1)
                .build();
        String sql = generator.generateAlterColumn("pms_lc_order", field);
        assertTrue(sql.contains("ALTER TABLE"), "应包含 ALTER TABLE");
        assertTrue(sql.contains("MODIFY COLUMN"), "应使用 MODIFY COLUMN");
        assertTrue(sql.contains("`remark`"), "应包含列名");
        assertTrue(sql.contains("VARCHAR(500)"), "应包含新类型");
    }
}
