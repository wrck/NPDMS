package cn.iocoder.yudao.module.pms.asset.dal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationSchemaContractTest {

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path migration = workingDirectory.resolve("sql/migrations/V65__asset_location_core.sql");
        if (!Files.exists(migration)) {
            migration = workingDirectory.resolve("../sql/migrations/V65__asset_location_core.sql").normalize();
        }
        sql = Files.readString(migration, StandardCharsets.UTF_8);
    }

    @Test
    void definesAstOwnedLocationTablesAndConstraints() {
        assertTrue(sql.contains("CREATE TABLE `ast_address`"));
        assertTrue(sql.contains("CREATE TABLE `ast_site`"));
        assertTrue(sql.contains("CREATE TABLE `ast_site_location`"));
        assertTrue(sql.contains("CREATE TABLE `ast_location_source_mapping`"));
        assertTrue(sql.contains("CREATE TABLE `ast_area_department_mapping`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_site_code`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_site_location_code`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_location_source_key`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ast_area_department_effective`"));
        assertTrue(sql.contains("'COUNTRY', 'PROVINCE', 'CITY', 'DISTRICT'"));
        assertTrue(sql.contains("'UNRESOLVED', 'RESOLVED'"));
    }

    @Test
    void usesApprovedAreaAndDepartmentFieldNames() {
        assertTrue(sql.contains("`area_code`"));
        assertTrue(sql.contains("`area_level`"));
        assertTrue(sql.contains("`department_code`"));
        assertFalse(sql.contains("administrative_division_code"));
        assertFalse(sql.contains("office_code"));
    }

    @Test
    void siteDoesNotBindCompanyOrDepartment() {
        String siteDdl = tableDdl("ast_site");
        assertTrue(siteDdl.contains("`customer_id` bigint NULL"));
        assertFalse(siteDdl.contains("company_id"));
        assertFalse(siteDdl.contains("department_id"));
        assertFalse(siteDdl.contains("department_code"));
    }

    @Test
    void addressFingerprintIsOnlyCandidateIndex() {
        String addressDdl = tableDdl("ast_address");
        assertTrue(addressDdl.contains("KEY `idx_ast_address_fingerprint`"));
        assertFalse(addressDdl.contains("UNIQUE KEY `uk_ast_address_fingerprint`"));
    }

    private static String tableDdl(String tableName) {
        int start = sql.indexOf("CREATE TABLE `" + tableName + "`");
        int end = sql.indexOf(";", start);
        return sql.substring(start, end);
    }

}
