package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.quartz.core.util.CronUtils;
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

class FileArtifactMigrationContractTest {

    private static String schemaSql;
    private static String seedSql;
    private static String applicationYaml;
    private static String quartzSql;

    @BeforeAll
    static void loadFiles() throws IOException {
        Path root = locateRepositoryRoot();
        schemaSql = Files.readString(root.resolve("sql/migrations/V92__fplt001_file_artifact.sql"),
                StandardCharsets.UTF_8);
        seedSql = Files.readString(root.resolve("sql/migrations/V93__fplt001_file_seed.sql"),
                StandardCharsets.UTF_8);
        applicationYaml = Files.readString(root.resolve("yudao-server/src/main/resources/application.yaml"),
                StandardCharsets.UTF_8);
        quartzSql = Files.readString(root.resolve("sql/migrations/V94__quartz_2_5_2_mysql_schema.sql"),
                StandardCharsets.UTF_8);
    }

    @Test
    void definesExactlyTheSixApprovedPlatformTables() {
        Matcher matcher = Pattern.compile("CREATE TABLE `plt_file_[a-z_]+`").matcher(schemaSql);
        int tableCount = 0;
        while (matcher.find()) {
            tableCount++;
        }
        assertEquals(6, tableCount);
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_artifact`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_version`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_reference`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_upload_session`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_access_grant`"));
        assertTrue(schemaSql.contains("CREATE TABLE `plt_file_archive_record`"));
    }

    @Test
    void keepsReferencesTenantScopedAndInsidePlatform() {
        assertTrue(schemaSql.contains("FOREIGN KEY (`tenant_id`, `artifact_id`)"));
        assertTrue(schemaSql.contains(
                "FOREIGN KEY (`tenant_id`, `artifact_id`, `file_version_no`)"));
        assertTrue(schemaSql.contains(
                "UNIQUE KEY `uk_plt_file_version_artifact_no`\n        (`tenant_id`, `artifact_id`, `version_no`)"));
        assertFalse(schemaSql.contains("REFERENCES `infra_"));
        assertFalse(schemaSql.contains("REFERENCES `proj_"));
        assertFalse(schemaSql.contains("REFERENCES `sol_"));
    }

    @Test
    void usesTheExactNonEmptyReferenceSlotKey() {
        assertTrue(schemaSql.contains(
                "(`tenant_id`, `owner_context`, `object_type`, `object_id`, `purpose_code`, `reference_key`)"));
        assertTrue(schemaSql.contains("CHECK (CHAR_LENGTH(TRIM(`reference_key`)) > 0)"));
        assertTrue(schemaSql.contains("UNIQUE KEY `uk_plt_file_version_infra_file` (`infra_file_id`)"));
    }

    @Test
    void seedsApprovedDictionariesPoliciesPermissionsAndJobWithoutRoleGrant() {
        assertTrue(seedSql.contains("pms_file_category"));
        assertTrue(seedSql.contains("pms_file_sensitivity_level"));
        assertTrue(seedSql.contains("CUSTOMER_DELAY_EVIDENCE"));
        assertTrue(seedSql.contains("\"priority\":100"));
        assertTrue(seedSql.contains("\"priority\":50"));
        assertTrue(seedSql.contains("\"enabled\":false"));
        assertTrue(seedSql.contains("pms:file:query"));
        assertTrue(seedSql.contains("pms:file:upload"));
        assertTrue(seedSql.contains("pms:file:download"));
        assertTrue(seedSql.contains("pms:file:preview"));
        assertTrue(seedSql.contains("pms:file:manage"));
        assertTrue(seedSql.contains("pms:file:archive"));
        assertTrue(seedSql.contains("fileOutboxDeliveryJob"));
        assertTrue(seedSql.contains("0/30 * * * * ?"));
        assertTrue(CronUtils.isValid("0/30 * * * * ?"));
        assertFalse(seedSql.contains("INSERT INTO `system_role_menu`"));
    }

    @Test
    void configuresTheApprovedMultipartBoundary() {
        assertTrue(applicationYaml.contains("max-file-size: 50MB"));
        assertTrue(applicationYaml.contains("max-request-size: 52MB"));
        assertFalse(applicationYaml.contains("max-file-size: 16MB"));
    }

    @Test
    void installsTheLockedQuartzSchemaWithoutDroppingRuntimeFacts() {
        assertEquals(11, countMatches(quartzSql, "CREATE TABLE QRTZ_"));
        assertTrue(quartzSql.contains("CREATE TABLE QRTZ_JOB_DETAILS"));
        assertTrue(quartzSql.contains("CREATE TABLE QRTZ_LOCKS"));
        assertTrue(quartzSql.contains("CREATE TABLE QRTZ_CRON_TRIGGERS"));
        assertFalse(Pattern.compile("(?im)^\\s*DROP\\s+TABLE").matcher(quartzSql).find());
        assertFalse(quartzSql.contains("INSERT INTO QRTZ_"));
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
