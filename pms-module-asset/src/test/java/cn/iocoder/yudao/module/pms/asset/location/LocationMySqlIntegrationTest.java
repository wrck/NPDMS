package cn.iocoder.yudao.module.pms.asset.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class LocationMySqlIntegrationTest {

    @Test
    void representativeLocationDataPreservesV18Invariants() throws Exception {
        try (Connection connection = openConnection()) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM ast_area_department_mapping "
                    + "WHERE tenant_id=1 AND area_code='330106' AND area_level='DISTRICT' "
                    + "AND mapping_type='SERVICE_OFFICE' AND status=0 AND effective_to IS NULL AND deleted=b'0'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM ast_area_department_mapping "
                    + "WHERE tenant_id=1 AND area_code='330108' AND area_level='DISTRICT' "
                    + "AND mapping_type='SERVICE_OFFICE' AND status=0 AND effective_to IS NULL AND deleted=b'0'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM ast_area_department_mapping "
                    + "WHERE tenant_id=1 AND area_code='999999' AND area_level='DISTRICT' "
                    + "AND mapping_type='SERVICE_OFFICE' AND status=0 AND effective_to IS NULL AND deleted=b'0'"));

            assertEquals(2, count(connection, "SELECT COUNT(*) FROM ast_site "
                    + "WHERE tenant_id=1 AND address_id=930810 AND status=0 AND deleted=b'0'"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM ast_site_location "
                    + "WHERE tenant_id=1 AND site_id=930811 AND deleted=b'0'"));
            assertEquals(5, count(connection, "SELECT MAX(tree_depth) FROM ast_site_location "
                    + "WHERE tenant_id=1 AND site_id=930811 AND deleted=b'0'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM ast_site_location "
                    + "WHERE id=930825 AND parent_id=930824 AND tree_path='/930820/930821/930822/930823/930824/'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM ast_location_source_mapping "
                    + "WHERE id=930840 AND match_status='PENDING' "
                    + "AND location_resolution_status='UNRESOLVED' AND address_id IS NULL AND site_id IS NULL"));
        }
    }

    @Test
    void locationWritesRollbackAsOneMySqlTransaction() throws Exception {
        String code = "IT-LOCATION-ROLLBACK-" + System.nanoTime();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO ast_address "
                        + "(tenant_id, country_code, country_name, detail_address, full_address, status, version, "
                        + "creator, updater, deleted) VALUES (1, 'CN', '中国', ?, ?, 0, 0, 'mysql-it', 'mysql-it', b'0')")) {
                    statement.setString(1, code);
                    statement.setString(2, code);
                    statement.executeUpdate();
                }
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM ast_address WHERE detail_address='" + code + "'"));
            } finally {
                connection.rollback();
            }
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM ast_address WHERE detail_address='" + code + "'"));
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static Connection openConnection() throws IOException, SQLException {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms");
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8",
                required(values, "NPDMS_DB_USER"), required(values, "NPDMS_DB_PASSWORD"));
    }

    private static Map<String, String> environment() throws IOException {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path dotenv = findDotenv();
        if (dotenv == null) {
            return values;
        }
        for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                continue;
            }
            int separator = value.indexOf('=');
            values.putIfAbsent(value.substring(0, separator).trim(), unquote(value.substring(separator + 1).trim()));
        }
        return values;
    }

    private static Path findDotenv() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("compose.yaml"))) {
                return Files.isRegularFile(path.resolve(".env")) ? path.resolve(".env") : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }
}
