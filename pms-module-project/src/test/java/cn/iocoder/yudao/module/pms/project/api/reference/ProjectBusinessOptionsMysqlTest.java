package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectBusinessOptionsQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/** Real Owner XML against a connection-local temporary table; not business acceptance. */
@EnabledIfSystemProperty(named = "inspection.mysql", matches = "true")
class ProjectBusinessOptionsMysqlTest {
    private static final String SCHEMA = "npdms_inspection_check_20261015_a13c35b";
    private static final String JDBC_PREFIX = "jdbc:mysql://127.0.0.1:24306/" + SCHEMA + "?";
    private Connection connection;
    private SqlSession session;
    private ProjectMasterMapper mapper;

    @BeforeEach
    void connectOnlyToTheApprovedSchemaAndShadowProjectsWithATemporaryTable() throws Exception {
        String url = System.getenv("INSPECTION_VERIFY_JDBC_URL");
        assertTrue(url != null && url.startsWith(JDBC_PREFIX), "只允许指定的巡检隔离 schema");
        String user = System.getenv("INSPECTION_VERIFY_DB_USER");
        String password = System.getenv("INSPECTION_VERIFY_DB_PASSWORD");
        assertNotNull(user, "缺少巡检验证数据库用户环境变量");
        assertNotNull(password, "缺少巡检验证数据库密码环境变量");
        connection = DriverManager.getConnection(url, user, password);
        assertEquals(SCHEMA, connection.getCatalog(), "连接 catalog 必须仍是批准的隔离 schema");
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TEMPORARY TABLE proj_project (
                      id BIGINT NOT NULL PRIMARY KEY,
                      tenant_id BIGINT NOT NULL,
                      deleted BIT NOT NULL DEFAULT b'0',
                      project_code VARCHAR(128),
                      project_name VARCHAR(300),
                      customer_name VARCHAR(300)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        }
        TenantContextHolder.setTenantId(1L);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setEnvironment(new Environment("inspection-project-options-temporary", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ProjectMasterMapper.class);
        for (String resource : List.of("/mapper/projectmanual/ProjectOverdueMapper.xml",
                "/mapper/projectmanual/ProjectMasterMapper.xml")) {
            try (var xml = getClass().getResourceAsStream(resource)) {
                assertNotNull(xml, "必须加载真实 Owner XML 及其引用片段");
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(false);
        mapper = session.getMapper(ProjectMasterMapper.class);
    }

    @AfterEach
    void rollbackThenCloseTheOnlyConnectionAndRemoveItsTemporaryTable() throws Exception {
        try {
            if (connection != null && !connection.isClosed()) connection.rollback();
        } finally {
            try {
                // Roll back before JdbcTransaction can restore auto-commit while closing.
                if (session != null) session.close();
            } finally {
                try { if (connection != null) connection.close(); }
                finally { TenantContextHolder.clear(); }
            }
        }
    }

    @Test
    void optionsIntersectVisibleIdsTenantAndLogicDeleteAndMapOnlyTheSelectorFields() throws Exception {
        insert(List.of(
                new ProjectFixture(11L, 1L, false, "P-11", "授权项目", "授权客户"),
                new ProjectFixture(12L, 1L, false, "P-12", "范围外项目", "范围外客户"),
                new ProjectFixture(13L, 1L, true, "P-13", "已删除项目", "已删除客户"),
                new ProjectFixture(14L, 2L, false, "P-14", "外租户项目", "外租户客户")));
        var visible = Set.of(11L, 13L, 14L);

        var rows = mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(1L, visible, ""));

        assertEquals(List.of(11L), ids(rows));
        var row = rows.getFirst();
        assertEquals("P-11", row.getProjectCode());
        assertEquals("授权项目", row.getProjectName());
        assertEquals("授权客户", row.getCustomerName());
        assertTrue(mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(2L, visible, "")).isEmpty(),
                "Query tenant=2 cannot override the tenant=1 interceptor");
        TenantContextHolder.setTenantId(2L); session.clearCache();
        assertTrue(mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(1L, visible, "")).isEmpty());
        assertEquals(List.of(14L), ids(mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(2L, visible, ""))));
    }

    @Test
    void emptyAndNullVisibleScopesNeverBecomeTenantWideQueries() throws Exception {
        insert(List.of(new ProjectFixture(11L, 1L, false, "P-11", "授权项目", "客户")));

        assertTrue(mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(1L, Set.of(), "")).isEmpty());
        assertTrue(mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(1L, null, "")).isEmpty());
    }

    @Test
    void percentAndUnderscoreAreLiteralWhileCaseAndChineseMatchWithinCodeOrName() throws Exception {
        insert(List.of(
                new ProjectFixture(21L, 1L, false, "P%Code", "Alpha项目", "客户甲"),
                new ProjectFixture(22L, 1L, false, "P_Code", "Beta项目", "客户乙"),
                new ProjectFixture(23L, 1L, false, "Normal", "百分比%项目", "客户丙"),
                new ProjectFixture(24L, 1L, false, "Other", "下划线_项目", "客户丁"),
                new ProjectFixture(25L, 1L, false, "PZCode", "普通项目", "仅客户字段命中")));
        var visible = Set.of(21L, 22L, 23L, 24L, 25L);

        assertEquals(Set.of(21L, 23L), Set.copyOf(ids(search(visible, "%"))));
        assertEquals(Set.of(22L, 24L), Set.copyOf(ids(search(visible, "_"))));
        assertEquals(List.of(21L), ids(search(visible, "p%code")));
        assertEquals(List.of(22L), ids(search(visible, "p_code")));
        assertEquals(List.of(21L), ids(search(visible, "aLPHa")));
        assertEquals(List.of(23L), ids(search(visible, "百分比")));
        assertEquals(List.of(24L), ids(search(visible, "下划线_")));
        assertEquals(List.of(25L), ids(search(visible, "普通项目")));
        assertTrue(search(visible, "仅客户字段命中").isEmpty(), "Options search covers project code/name, not customer text");
        assertTrue(search(visible, "不存在").isEmpty());
    }

    @Test
    void optionsSortByProjectNameThenIdInsteadOfInsertionOrderOrCode() throws Exception {
        insert(List.of(
                new ProjectFixture(34L, 1L, false, "A", "Beta", "客户34"),
                new ProjectFixture(32L, 1L, false, "Z", "Alpha", "客户32"),
                new ProjectFixture(31L, 1L, false, "Y", "Alpha", "客户31"),
                new ProjectFixture(33L, 1L, false, "B", "Beta", "客户33")));

        assertEquals(List.of(31L, 32L, 33L, 34L), ids(search(Set.of(31L, 32L, 33L, 34L), "")));
    }

    @Test
    void optionsReturnAtMostOneHundredSortedMatchesWithTheExpectedFieldMapping() throws Exception {
        var fixtures = new ArrayList<ProjectFixture>();
        for (long id = 105; id >= 1; id--) {
            fixtures.add(new ProjectFixture(id, 1L, false, "CODE-" + id,
                    "项目-" + String.format(java.util.Locale.ROOT, "%03d", id), "客户-" + id));
        }
        insert(fixtures);
        var visible = LongStream.rangeClosed(1, 105).boxed().collect(Collectors.toSet());

        var rows = search(visible, "项目-");

        assertEquals(100, rows.size());
        assertEquals(LongStream.rangeClosed(1, 100).boxed().toList(), ids(rows));
        assertEquals("CODE-100", rows.getLast().getProjectCode());
        assertEquals("项目-100", rows.getLast().getProjectName());
        assertEquals("客户-100", rows.getLast().getCustomerName());
    }

    private List<ProjectMasterDO> search(Set<Long> visible, String keyword) {
        return mapper.selectBusinessOptions(new ProjectBusinessOptionsQuery(1L, visible, keyword));
    }

    private static List<Long> ids(List<ProjectMasterDO> rows) {
        return rows.stream().map(ProjectMasterDO::getId).toList();
    }

    private record ProjectFixture(Long id, Long tenantId, boolean deleted, String code, String name, String customer) { }

    private void insert(List<ProjectFixture> fixtures) throws SQLException {
        assertFalse(connection.getAutoCommit(), "临时表 fixture 必须留在同连接事务内");
        // The temporary table was created on this exact connection before any fixture writes.
        try (var statement = connection.prepareStatement("""
                INSERT INTO proj_project(id,tenant_id,deleted,project_code,project_name,customer_name)
                VALUES(?,?,?,?,?,?)
                """)) {
            for (var fixture : fixtures) {
                statement.setLong(1, fixture.id()); statement.setLong(2, fixture.tenantId());
                statement.setBoolean(3, fixture.deleted()); statement.setString(4, fixture.code());
                statement.setString(5, fixture.name()); statement.setString(6, fixture.customer());
                assertEquals(1, statement.executeUpdate());
            }
        }
        session.clearCache();
    }
}
