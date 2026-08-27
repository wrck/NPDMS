package cn.iocoder.yudao.module.pms.platform.dynamicform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicFormMigrationContractTest {

    private static String schemaSql;
    private static String seedSql;

    @BeforeAll
    static void loadMigrations() throws IOException {
        Path migrationDirectory = locateRepositoryRoot().resolve("sql/migrations");
        Path schemaMigration = migrationDirectory.resolve("V102__fplt002_dynamic_form.sql");
        Path seedMigration = migrationDirectory.resolve("V103__fplt002_dynamic_form_seed.sql");
        assertTrue(Files.exists(schemaMigration));
        assertTrue(Files.exists(seedMigration));
        assertTrue(schemaMigration.getFileName().toString().compareTo(seedMigration.getFileName().toString()) < 0);
        schemaSql = Files.readString(schemaMigration, StandardCharsets.UTF_8);
        seedSql = Files.readString(seedMigration, StandardCharsets.UTF_8);
    }

    @Test
    void createsOnlyTheThreeApprovedDynamicFormTablesWithoutDestructiveDdl() {
        Matcher matcher = Pattern.compile("CREATE TABLE `plt_dynamic_form_[a-z_]+`").matcher(schemaSql);
        int tableCount = 0;
        while (matcher.find()) {
            tableCount++;
        }
        assertEquals(3, tableCount);
        assertTrue(schemaSql.contains("CREATE TABLE `plt_dynamic_form_template`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_dynamic_form_template_revision`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_dynamic_form_instance`"));
        assertFalse(Pattern.compile("(?im)^\\s*(DROP|TRUNCATE|DELETE)\\b").matcher(schemaSql).find());
        assertFalse(schemaSql.toUpperCase().contains("AUTO_INCREMENT"));
    }

    @Test
    void enforcesTenantLocalTemplateRevisionAndInstanceIdentity() {
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_plt_dynamic_form_template_code` (`tenant_id`, `template_code`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_plt_dynamic_form_revision_draft` (`tenant_id`, `template_id`, `draft_marker`)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_plt_dynamic_form_instance_owner`"));
        assertTrue(schemaSql.contains("CONSTRAINT `fk_plt_dynamic_form_revision_template`"));
        assertTrue(schemaSql.contains("CONSTRAINT `fk_plt_dynamic_form_revision_source`"));
        assertTrue(schemaSql.contains("CONSTRAINT `fk_plt_dynamic_form_instance_revision`"));
        assertTrue(schemaSql.contains("CONSTRAINT `fk_plt_dynamic_form_template_published_revision`"));
        assertTrue(schemaSql.contains("FOREIGN KEY (`tenant_id`, `current_published_revision_id`, `id`)"));
    }

    @Test
    void constrainsDraftMarkerAndNativeJsonRoots() {
        assertTrue(schemaSql.contains("CHECK ((`status_code` = 'DRAFT' AND `draft_marker` = 1)"));
        assertTrue(schemaSql.contains("(`status_code` = 'PUBLISHED' AND `draft_marker` IS NULL)"));
        assertTrue(schemaSql.contains("JSON_TYPE(`form_conf_json`) = 'OBJECT'"));
        assertTrue(schemaSql.contains("JSON_TYPE(`form_rules_json`) = 'ARRAY'"));
        assertTrue(schemaSql.contains("JSON_TYPE(`value_json`) = 'OBJECT'"));
    }

    @Test
    void seedsExactlySixPermissionsWithoutRoleGrant() {
        assertEquals(6, countMatches(seedSql, "'pms:dynamic-form-"));
        assertTrue(seedSql.contains("'pms:dynamic-form-template:query'"));
        assertTrue(seedSql.contains("'pms:dynamic-form-template:manage'"));
        assertTrue(seedSql.contains("'pms:dynamic-form-template:publish'"));
        assertTrue(seedSql.contains("'pms:dynamic-form-instance:query'"));
        assertTrue(seedSql.contains("'pms:dynamic-form-instance:create'"));
        assertTrue(seedSql.contains("'pms:dynamic-form-instance:update'"));
        assertFalse(seedSql.contains("INSERT INTO `system_role_menu`"));
    }

    @Test
    void seedsTheThreeSelectionStatesAndRepresentativeRulesWithoutOverwritingRevisions() {
        assertTrue(seedSql.contains("'PLT_EXAMPLE_GENERAL_FORM'"));
        assertTrue(seedSql.contains("'PLT_EXAMPLE_DISABLED_FORM'"));
        assertTrue(seedSql.contains("'PLT_EXAMPLE_DRAFT_FORM'"));
        assertTrue(seedSql.contains("'ENABLED'"));
        assertTrue(seedSql.contains("'DISABLED'"));
        assertTrue(seedSql.contains("'PUBLISHED'"));
        assertTrue(seedSql.contains("'DRAFT'"));
        assertTrue(seedSql.contains("\"type\":\"Editor\""));
        assertTrue(seedSql.contains("\"type\":\"UploadFile\""));
        assertTrue(seedSql.contains("\"type\":\"PmsFileArtifact\""));
        assertTrue(seedSql.contains("'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38'"));
        assertFalse(seedSql.contains("UPDATE `plt_dynamic_form_template_revision`"));
        assertFalse(seedSql.contains("proj_work_binding"));
        assertFalse(seedSql.contains("sol_requirement_analysis"));
    }

    @Test
    void seedsTheApprovedFileCategoryAndStableIdentifiers() {
        assertTrue(seedSql.contains("992202030001"));
        assertTrue(seedSql.contains("'pms_file_category'"));
        assertTrue(seedSql.contains("'DYNAMIC_FORM_ATTACHMENT'"));
        assertTrue(seedSql.contains("992202010001"));
        assertTrue(seedSql.contains("992202020001"));
        assertTrue(seedSql.contains("(198800,"));
        assertTrue(seedSql.contains("(198805,"));
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
