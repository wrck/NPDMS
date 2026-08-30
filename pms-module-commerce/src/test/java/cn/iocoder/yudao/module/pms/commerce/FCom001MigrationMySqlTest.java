package cn.iocoder.yudao.module.pms.commerce;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class FCom001MigrationMySqlTest {

    private static Connection connection;
    private static long tenantId;

    @BeforeAll
    static void connect() throws SQLException {
        Map<String, String> environment = System.getenv();
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        String database = environment.getOrDefault("NPDMS_MYSQL_DATABASE", "npdms");
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        connection = DriverManager.getConnection(url,
                required(environment, "NPDMS_DB_USER"), required(environment, "NPDMS_DB_PASSWORD"));
        tenantId = 980_143_000_000L + Math.abs(System.nanoTime() % 1_000_000L);
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM com_delivery_scope_detail WHERE tenant_id=?")) {
                statement.setLong(1, tenantId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM com_delivery_scope WHERE tenant_id=?")) {
                statement.setLong(1, tenantId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM com_order_line WHERE tenant_id=?")) {
                statement.setLong(1, tenantId);
                statement.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    void exposesAllTenCommerceTablesAfterFullMigration() throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                + "AND table_name IN ('com_contract','com_sales_order','com_sales_order_contract_relation',"
                + "'com_project_contract_relation','com_authority_candidate','com_order_line',"
                + "'com_delivery_scope','com_delivery_scope_detail','com_delivery_scope_project_version',"
                + "'com_outbox_event')";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(10, result.getInt(1));
        }
        assertColumnLength("com_contract", "source_version", 64);
        assertColumnLength("com_sales_order", "source_version", 64);
        assertColumnLength("com_authority_candidate", "matched_owner_source_version", 64);
    }

    @Test
    void preservesLegacyNullAndZeroOrderFactsAndNullDetailDimensions() throws SQLException {
        long lineId = tenantId + 1;
        insertOrderLine(lineId, null, null, null);
        insertOrderLine(tenantId + 4, BigDecimal.ZERO, null, null);
        long scopeId = tenantId + 2;
        insertScope(scopeId, lineId, "ACTIVE", 1L);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO com_delivery_scope_detail "
                        + "(id,delivery_scope_id,allocated_qty,detail_status,tenant_id) VALUES (?,?,?,?,?)")) {
            statement.setLong(1, tenantId + 3);
            statement.setLong(2, scopeId);
            statement.setBigDecimal(3, BigDecimal.ONE);
            statement.setString(4, "ACTIVE");
            statement.setLong(5, tenantId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test
    void rejectsSecondActiveOrConflictCurrentRow() throws SQLException {
        long lineId = tenantId + 10;
        insertOrderLine(lineId, BigDecimal.ONE, "台", "ACTIVE");
        insertScope(tenantId + 11, lineId, "ACTIVE", 1L);
        assertThrows(SQLException.class, () -> insertScope(tenantId + 12, lineId, "CONFLICT", 2L));
    }

    @Test
    void rejectsQualifiedSerialDetailWhoseQuantityIsNotOne() throws SQLException {
        long lineId = tenantId + 20;
        insertOrderLine(lineId, BigDecimal.TEN, "台", "ACTIVE");
        long scopeId = tenantId + 21;
        insertScope(scopeId, lineId, "ACTIVE", 1L);
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO com_delivery_scope_detail "
                            + "(id,delivery_scope_id,serial_no,allocated_qty,unit_code,product_code,site_id,"
                            + "site_location_id,location_resolution_status,detail_status,tenant_id) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setLong(1, tenantId + 22);
                statement.setLong(2, scopeId);
                statement.setString(3, "SN-1");
                statement.setBigDecimal(4, BigDecimal.TEN);
                statement.setString(5, "台");
                statement.setString(6, "P-1");
                statement.setLong(7, 1L);
                statement.setLong(8, 2L);
                statement.setString(9, "RESOLVED");
                statement.setString(10, "ACTIVE");
                statement.setLong(11, tenantId);
                statement.executeUpdate();
            }
        });
    }

    private static void insertOrderLine(long id, BigDecimal quantity, String unit, String lifecycle)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO com_order_line (id,source_system,source_key,source_version,order_id,line_code,"
                        + "quantity,unit_code,quantity_status,source_lifecycle_status,synced_at,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,NOW(3),?)")) {
            statement.setLong(1, id);
            statement.setString(2, "TEST");
            statement.setString(3, "LINE-" + id);
            statement.setString(4, "V1");
            statement.setLong(5, id);
            statement.setString(6, "L-" + id);
            statement.setBigDecimal(7, quantity);
            statement.setString(8, unit);
            statement.setString(9, "CONFIRMED");
            statement.setString(10, lifecycle);
            statement.setLong(11, tenantId);
            statement.executeUpdate();
        }
    }

    private static void insertScope(long id, long orderLineId, String status, long allocationVersion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO com_delivery_scope (id,order_line_id,project_id,allocated_qty,scope_status,"
                        + "allocation_version,source_evidence,effective_from,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,NOW(3),?)")) {
            statement.setLong(1, id);
            statement.setLong(2, orderLineId);
            statement.setLong(3, tenantId + 100);
            statement.setBigDecimal(4, BigDecimal.ONE);
            statement.setString(5, status);
            statement.setLong(6, allocationVersion);
            statement.setString(7, "TEST");
            statement.setLong(8, tenantId);
            statement.executeUpdate();
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    private static void assertColumnLength(String table, String column, int expected) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals(expected, result.getInt(1));
            }
        }
    }
}
