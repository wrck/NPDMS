package cn.iocoder.yudao.module.pms.engineering.service.installation;

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
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class InstallationLocationMySqlIntegrationTest {

    @Test
    void installMoveAndRemoveLocationRollbackTogether() throws Exception {
        String prefix = "IT-LOCATION-" + System.nanoTime();
        try (Connection connection = openConnection()) {
            long equipmentId = availableEquipment(connection);
            EquipmentLocation before = equipmentLocation(connection, equipmentId);
            connection.setAutoCommit(false);
            try {
                long firstInstallation = insertInstallation(connection, prefix + "-A", equipmentId,
                        930811L, 930825L, LocalDateTime.now());
                effectEquipment(connection, equipmentId, 930811L, 930825L, "RESOLVED", firstInstallation);
                assertEquals(930825L, equipmentLocation(connection, equipmentId).siteLocationId());

                closeInstallation(connection, firstInstallation);
                long secondInstallation = insertInstallation(connection, prefix + "-B", equipmentId,
                        930811L, 930824L, LocalDateTime.now().plusSeconds(1));
                effectEquipment(connection, equipmentId, 930811L, 930824L, "RESOLVED", secondInstallation);
                assertEquals(930824L, equipmentLocation(connection, equipmentId).siteLocationId());

                closeInstallation(connection, secondInstallation);
                effectEquipment(connection, equipmentId, null, null, "UNRESOLVED", null);
                EquipmentLocation removed = equipmentLocation(connection, equipmentId);
                assertNull(removed.siteId());
                assertNull(removed.siteLocationId());
                assertEquals("UNRESOLVED", removed.resolutionStatus());
            } finally {
                connection.rollback();
            }
            assertEquals(before, equipmentLocation(connection, equipmentId));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM pms_eng_installation WHERE code LIKE '" + prefix + "%'"));
        }
    }

    private static long availableEquipment(Connection connection) throws SQLException {
        String sql = "SELECT e.id FROM pms_equipment e LEFT JOIN pms_eng_installation i "
                + "ON i.tenant_id=e.tenant_id AND i.current_equipment_id=e.id "
                + "WHERE e.tenant_id=1 AND e.deleted=b'0' AND i.id IS NULL ORDER BY e.id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new IllegalStateException("没有可用于安装位置集成测试的设备");
            }
            return resultSet.getLong(1);
        }
    }

    private static long insertInstallation(Connection connection, String code, long equipmentId,
                                           long siteId, long locationId, LocalDateTime effectiveFrom)
            throws SQLException {
        String sql = "INSERT INTO pms_eng_installation "
                + "(project_id, code, equipment_id, install_location, install_time, address_id, address_version, "
                + "site_id, site_version, site_location_id, site_location_version, location_resolution_status, "
                + "address_snapshot, location_snapshot, effective_from, status, version, creator, updater, deleted, tenant_id) "
                + "VALUES (1001, ?, ?, '杭州数据中心A站', ?, 930810, 0, ?, 0, ?, 0, 'RESOLVED', '{}', '{}', ?, 2, 0, "
                + "'mysql-it', 'mysql-it', b'0', 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setLong(2, equipmentId);
            statement.setTimestamp(3, Timestamp.valueOf(effectiveFrom));
            statement.setLong(4, siteId);
            statement.setLong(5, locationId);
            statement.setTimestamp(6, Timestamp.valueOf(effectiveFrom));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void closeInstallation(Connection connection, long installationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pms_eng_installation SET effective_to=NOW(3) WHERE id=?")) {
            statement.setLong(1, installationId);
            statement.executeUpdate();
        }
    }

    private static void effectEquipment(Connection connection, long equipmentId, Long siteId, Long locationId,
                                        String resolutionStatus, Long installationId) throws SQLException {
        String sql = "UPDATE pms_equipment SET site_id=?, site_location_id=?, location_resolution_status=?, "
                + "location_snapshot='{}', location_effective_from=NOW(3), location_source_installation_id=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (siteId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, siteId);
            }
            if (locationId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, locationId);
            }
            statement.setString(3, resolutionStatus);
            if (installationId == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, installationId);
            }
            statement.setLong(5, equipmentId);
            statement.executeUpdate();
        }
    }

    private static EquipmentLocation equipmentLocation(Connection connection, long equipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT site_id, site_location_id, "
                + "location_resolution_status, location_source_installation_id FROM pms_equipment WHERE id=?")) {
            statement.setLong(1, equipmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new EquipmentLocation(nullableLong(resultSet, 1), nullableLong(resultSet, 2),
                        resultSet.getString(3), nullableLong(resultSet, 4));
            }
        }
    }

    private static Long nullableLong(ResultSet resultSet, int column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
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
        if (dotenv != null) {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                    continue;
                }
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(), unquote(value.substring(separator + 1).trim()));
            }
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

    private record EquipmentLocation(Long siteId, Long siteLocationId, String resolutionStatus,
                                     Long sourceInstallationId) {
    }
}
