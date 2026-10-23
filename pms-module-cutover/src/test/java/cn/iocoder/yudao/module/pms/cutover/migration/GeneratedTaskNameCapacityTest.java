package cn.iocoder.yudao.module.pms.cutover.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedTaskNameCapacityTest {
    @Test void forwardMigrationPreservesExistingNamesAndAcceptsFullProjectNameWithActualNumber() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:task_name_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var tables = List.of("cut_task", "ast_device_cutover_result", "srv_inspection_task", "cus_inspection_history");
        var migration = Files.readString(Path.of("../sql/migrations/V486__generated_cutover_inspection_task_names.sql"))
                .replaceAll("(?m)^--.*$", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            for (String table : tables) {
                sql.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, task_name VARCHAR(128))");
                sql.execute("INSERT INTO " + table + " VALUES (1, '原任务名称')");
            }
            // 执行真实前向SQL，并确认重复执行不会改写历史值。
            for (int run = 0; run < 2; run++) {
                for (String statement : migration.split(";")) if (!statement.isBlank()) sql.execute(statement);
            }
            String name = "项".repeat(255) + "-割接-CUT-" + Long.MAX_VALUE;
            assertEquals(282, name.length());
            for (String table : tables) {
                try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES (2, ?)")) {
                    insert.setString(1, name); assertEquals(1, insert.executeUpdate());
                }
                try (var rows = sql.executeQuery("SELECT task_name FROM " + table + " ORDER BY id")) {
                    assertTrue(rows.next()); assertEquals("原任务名称", rows.getString(1));
                    assertTrue(rows.next()); assertEquals(name, rows.getString(1));
                }
            }
        }
    }
}
