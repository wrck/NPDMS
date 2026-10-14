package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleCandidateFactMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 使用实际 Mapper XML 和隔离 MySQL；临时表仅属于本连接，不写入既有业务表。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class RoleCandidateFactMySqlTest {
    private SqlSession session;
    private RoleCandidateFactApi api;
    private final RoleCandidateFactApi.Query role = new RoleCandidateFactApi.Query(1L, "probSupporter");
    private final RoleCandidateFactApi.ActorQuery actor = new RoleCandidateFactApi.ActorQuery(1L, 8L);

    @BeforeEach
    void openIsolatedConnection() throws Exception {
        String schema = required("NPDMS_DB_NAME");
        assertTrue(schema.matches("npdms_cut_structured_[0-9]{8}_r[0-9]+"));
        assertEquals("24306", required("NPDMS_MYSQL_PORT"));
        var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:24306/" + schema
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                required("NPDMS_DB_USER"), required("NPDMS_DB_PASSWORD"));
        var configuration = new Configuration(new Environment("cut-role-isolated", new JdbcTransactionFactory(), source));
        String resource = "mapper/permission/RoleCandidateFactMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
        assertEquals(schema, session.getConnection().getCatalog());
        execute("SET time_zone='+00:00'");
        execute("""
                CREATE TEMPORARY TABLE system_users (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, status INT DEFAULT 0,
                  deleted BIT DEFAULT b'0', update_time DATETIME(6))
                """);
        execute("""
                CREATE TEMPORARY TABLE system_role (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, code VARCHAR(80), status INT DEFAULT 0,
                  deleted BIT DEFAULT b'0', update_time DATETIME(6))
                """);
        execute("""
                CREATE TEMPORARY TABLE system_user_role (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, user_id BIGINT, role_id BIGINT,
                  deleted BIT DEFAULT b'0', create_time DATETIME(6), update_time DATETIME(6))
                """);
        execute("INSERT INTO system_users (id,tenant_id,update_time) VALUES (8,1,'2026-10-14 04:00:00')");
        execute("INSERT INTO system_role (id,tenant_id,code,update_time) VALUES (20,1,'probSupporter','2017-02-08 09:08:38')");
        execute("INSERT INTO system_user_role (id,tenant_id,user_id,role_id) VALUES (30,1,8,20)");
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(false);
        api = new RoleCandidateFactApiImpl(session.getMapper(RoleCandidateFactMapper.class), mock(RoleMapper.class));
    }

    @AfterEach
    void closeIsolatedConnection() {
        TenantContextHolder.clear();
        if (session != null) { session.rollback(); session.close(); }
    }

    @Test
    void historicalMembershipWithoutTimestampsCanBeReadAndLocked() {
        var expected = new RoleCandidateFactApi.Candidate(8L, 20L,
                micros("2017-02-08T09:08:38Z"), micros("2026-10-14T04:00:00Z"));
        assertEquals(List.of(expected), api.inspect(role));
        assertEquals(List.of(expected), api.lockAndInspect(role));
        var membership = new RoleCandidateFactApi.ActorMembership(expected.userId(), expected.roleId(),
                "probSupporter", expected.membershipWatermark(), expected.userWatermark());
        assertEquals(List.of(membership), api.inspectActor(actor));
        assertEquals(List.of(membership), api.lockAndInspectActor(actor));
    }

    @Test
    void membershipAndRoleChangesAdvanceWatermarkAndRevocationRemovesCandidate() throws SQLException {
        execute("UPDATE system_user_role SET create_time='2026-10-14 04:01:00' WHERE id=30");
        assertEquals(micros("2026-10-14T04:01:00Z"), api.inspect(role).getFirst().membershipWatermark());
        execute("UPDATE system_user_role SET update_time='2026-10-14 04:02:00' WHERE id=30");
        assertEquals(micros("2026-10-14T04:02:00Z"), api.lockAndInspectActor(actor).getFirst().membershipWatermark());
        execute("UPDATE system_role SET update_time='2026-10-14 04:03:00' WHERE id=20");
        assertEquals(micros("2026-10-14T04:03:00Z"), api.lockAndInspect(role).getFirst().membershipWatermark());
        execute("UPDATE system_user_role SET deleted=b'1' WHERE id=30");
        assertTrue(api.inspect(role).isEmpty());
        assertTrue(api.lockAndInspectActor(actor).isEmpty());
    }

    @Test
    void disabledDeletedAndForeignTenantFactsCannotBecomeCandidates() throws SQLException {
        execute("""
                INSERT INTO system_users (id,tenant_id,status,deleted,update_time) VALUES
                (9,1,0,b'0','2026-10-14 04:00:00'), (10,2,0,b'0','2026-10-14 04:00:00'),
                (11,1,1,b'0','2026-10-14 04:00:00'), (12,1,0,b'1','2026-10-14 04:00:00')
                """);
        execute("""
                INSERT INTO system_user_role (id,tenant_id,user_id,role_id) VALUES
                (31,2,9,20), (32,1,10,20), (33,1,11,20), (34,1,12,20)
                """);
        assertEquals(List.of(8L), api.inspect(role).stream().map(RoleCandidateFactApi.Candidate::userId).toList());
        assertTrue(api.inspectActor(new RoleCandidateFactApi.ActorQuery(1L, 9L)).isEmpty());
        assertTrue(api.inspectActor(new RoleCandidateFactApi.ActorQuery(1L, 10L)).isEmpty());
        execute("UPDATE system_role SET status=1 WHERE id=20");
        assertTrue(api.lockAndInspect(role).isEmpty());
        assertTrue(api.lockAndInspectActor(actor).isEmpty());
    }

    private void execute(String sql) throws SQLException {
        try (var statement = session.getConnection().createStatement()) { statement.execute(sql); }
        session.clearCache();
    }
    private static Long micros(String timestamp) { return Instant.parse(timestamp).toEpochMilli() * 1000; }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment: " + name);
        return value;
    }
}
