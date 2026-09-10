package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalEditableDeleteQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL and production Mapper/interceptor; all writes use a connection-private temporary table. */
@EnabledIfSystemProperty(named = "arrival.mysql", matches = "true")
class ArrivalDeleteMySqlTest {
    @Test void deleteRespectsConcurrentSigningVersionTenantAndLogicalDeletion() throws Exception {
        assertEquals("npdms_test", System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316", System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"))) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TEMPORARY TABLE pms_eng_arrival (id BIGINT PRIMARY KEY, tenant_id BIGINT, "
                        + "status INT, version INT, deleted BIT DEFAULT b'0', updater VARCHAR(64), update_time DATETIME)");
                sql.execute("INSERT INTO pms_eng_arrival (id,tenant_id,status,version) VALUES "
                        + "(1,1,0,6),(2,1,0,6),(3,1,2,6),(4,1,1,6)");
            }
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("arrival-delete-test", new JdbcTransactionFactory(),
                    new SingleConnectionDataSource(connection, true)));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            configuration.addInterceptor(interceptor);
            configuration.addMapper(ArrivalMapper.class);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                var mapper = session.getMapper(ArrivalMapper.class);
                TenantContextHolder.setTenantId(1L);
                // A signing transaction wins after the delete caller read version 6.
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("UPDATE pms_eng_arrival SET status=1,version=7 WHERE id=1");
                }
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(1L, 6)));
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(1L, 7)));
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(2L, 5)));
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(4L, 6)));

                TenantContextHolder.setTenantId(2L);
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(2L, 6)));
                TenantContextHolder.setTenantId(1L);
                assertEquals(1, mapper.deleteEditable(new ArrivalEditableDeleteQuery(2L, 6)));
                assertEquals(0, mapper.deleteEditable(new ArrivalEditableDeleteQuery(2L, 6)));
                assertEquals(1, mapper.deleteEditable(new ArrivalEditableDeleteQuery(3L, 6)));
                try (var sql = connection.createStatement(); var result = sql.executeQuery(
                        "SELECT COUNT(*) FROM pms_eng_arrival WHERE status=1 AND deleted=b'0'")) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                }
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
