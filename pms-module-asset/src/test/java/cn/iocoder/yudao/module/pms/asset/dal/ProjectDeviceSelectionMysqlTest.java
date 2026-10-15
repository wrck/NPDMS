package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceSelectionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real candidate XML in the approved scratch schema; fixture rows always roll back, no business acceptance. */
@EnabledIfSystemProperty(named = "inspection.mysql", matches = "true")
class ProjectDeviceSelectionMysqlTest {
    private static final String SCHEMA = "npdms_inspection_check_20261015_a13c35b";
    private static final String JDBC_PREFIX = "jdbc:mysql://127.0.0.1:24306/" + SCHEMA + "?";
    private static final long DEVICE_BASE = 9701019000L;
    private static final Long PROJECT = 9701019901L;
    private static final Long OTHER_PROJECT = 9701019902L;
    private static final Long CUSTOMER = 9701019991L;
    private static final String CONTRACT = "INSPECTION-VERIFY-C9701019901";
    private static final String OTHER_CONTRACT = "INSPECTION-VERIFY-OTHER";
    private static final String SN_PREFIX = "INSPECTION-VERIFY-SELECT-";
    private Connection connection;
    private SqlSession session;
    private ProjectDeviceSelectionMapper mapper;

    @BeforeEach
    void connectOnlyToTheApprovedScratchAndInsertRollbackOnlyFixtures() throws Exception {
        String url = System.getenv("INSPECTION_VERIFY_JDBC_URL");
        assertTrue(url != null && url.startsWith(JDBC_PREFIX), "只允许指定的巡检隔离 schema 和端口");
        String user = System.getenv("INSPECTION_VERIFY_DB_USER");
        String password = System.getenv("INSPECTION_VERIFY_DB_PASSWORD");
        assertNotNull(user, "缺少巡检验证数据库用户环境变量");
        assertNotNull(password, "缺少巡检验证数据库密码环境变量");
        connection = DriverManager.getConnection(url, user, password);
        assertEquals(SCHEMA, connection.getCatalog(), "连接 catalog 必须是批准的隔离 schema");
        connection.setAutoCommit(false);
        requireExistingTransactionalTable();
        TenantContextHolder.setTenantId(1L);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setEnvironment(new Environment("inspection-device-candidates-scratch", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ProjectDeviceSelectionMapper.class);
        String resource = "/mapper/device/ProjectDeviceSelectionMapper.xml";
        try (var xml = getClass().getResourceAsStream(resource)) {
            assertNotNull(xml, "必须加载实际项目设备选择器 XML");
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(false);
        mapper = session.getMapper(ProjectDeviceSelectionMapper.class);
        insert(List.of(
                new DeviceFixture(1, 1L, PROJECT, OTHER_CONTRACT, "PROJECT-ONE", "核心交换机", "MODEL-A", false),
                new DeviceFixture(2, 1L, OTHER_PROJECT, CONTRACT, "CONTRACT-TWO", "核心防火墙", "MODEL-B", false),
                new DeviceFixture(3, 1L, PROJECT, CONTRACT, "BOTH-THREE", "核心交换机", "MODEL-A", false),
                new DeviceFixture(4, 1L, OTHER_PROJECT, OTHER_CONTRACT, "OUTSIDE-FOUR", "核心交换机", "MODEL-A", false),
                new DeviceFixture(5, 2L, PROJECT, CONTRACT, "FOREIGN-FIVE", "外租户项目设备", "MODEL-A", false),
                new DeviceFixture(6, 1L, PROJECT, CONTRACT, "DELETED-SIX", "已删除双命中", "MODEL-A", true),
                new DeviceFixture(7, 1L, OTHER_PROJECT, "", "BLANK-SEVEN", "无关联空合同", "MODEL-A", false),
                new DeviceFixture(8, 1L, PROJECT, null, "PROJECT-EIGHT", "项目附属设备", "MODEL-C", false),
                new DeviceFixture(9, 2L, OTHER_PROJECT, CONTRACT, "FOREIGN-NINE", "外租户合同设备", "MODEL-B", false),
                new DeviceFixture(10, 1L, PROJECT, OTHER_CONTRACT, "DELETED-TEN", "已删除项目设备", "MODEL-A", true)));
    }

    @AfterEach
    void alwaysRollbackBeforeClosingTheOnlyConnection() throws Exception {
        try {
            if (connection != null && !connection.isClosed()) connection.rollback();
        } finally {
            try {
                // Explicit rollback precedes JdbcTransaction restoring auto-commit during close.
                if (session != null) session.close();
            } finally {
                try { if (connection != null) connection.close(); }
                finally { TenantContextHolder.clear(); }
            }
        }
    }

    @Test
    void projectOrContractCandidatesAreDeduplicatedCountedAndPagedByDescendingIdWithFieldsMapped() {
        var query = query(); query.setPageSize(2);

        var first = mapper.selectCandidatePage(query);

        assertPage(first, 4, List.of(DEVICE_BASE + 8, DEVICE_BASE + 3));
        var both = first.getList().getLast();
        assertEquals(SN_PREFIX + "BOTH-THREE", both.getSn());
        assertEquals("核心交换机", both.getName());
        assertEquals("VERIFY-CODE-3", both.getProductCode());
        assertEquals("MODEL-A", both.getProductModel());
        assertEquals(CONTRACT, both.getContractNo());
        assertEquals(PROJECT, both.getProjectId());
        assertEquals(CUSTOMER, both.getCustomerId());
        assertEquals("IN_SERVICE", both.getStatus());

        query.setPageNo(2);
        assertPage(mapper.selectCandidatePage(query), 4, List.of(DEVICE_BASE + 2, DEVICE_BASE + 1));
        query.setPageNo(3);
        assertPage(mapper.selectCandidatePage(query), 4, List.of());
        query.setPageNo(1); query.setPageSize(100);
        var all = mapper.selectCandidatePage(query);
        assertPage(all, 4, List.of(DEVICE_BASE + 8, DEVICE_BASE + 3, DEVICE_BASE + 2, DEVICE_BASE + 1));
        assertEquals(4, all.getList().stream().map(DeviceDO::getId).distinct().count(),
                "A device matching both UNION branches must appear exactly once");
    }

    @Test
    void serialNameModelAndContractFiltersApplyAfterEitherCandidateBranch() {
        var query = query(); query.setSn("BOTH-THREE");
        assertPage(mapper.selectCandidatePage(query), 1, List.of(DEVICE_BASE + 3));
        query.setSn(null); query.setName("交换机");
        assertPage(mapper.selectCandidatePage(query), 2, List.of(DEVICE_BASE + 3, DEVICE_BASE + 1));
        query.setName(null); query.setProductModel("MODEL-B");
        assertPage(mapper.selectCandidatePage(query), 1, List.of(DEVICE_BASE + 2));
        query.setProductModel(null); query.setContractNo("C9701019901");
        assertPage(mapper.selectCandidatePage(query), 2, List.of(DEVICE_BASE + 3, DEVICE_BASE + 2));
        query.setSn("BOTH"); query.setName("交换机"); query.setProductModel("MODEL-A");
        assertPage(mapper.selectCandidatePage(query), 1, List.of(DEVICE_BASE + 3));
        query.setName("防火墙");
        assertPage(mapper.selectCandidatePage(query), 0, List.of());
    }

    @Test
    void bothUnionBranchesAndTheOuterReadKeepTenantAndLogicDeleteBoundaries() {
        var query = query();
        assertPage(mapper.selectCandidatePage(query), 4,
                List.of(DEVICE_BASE + 8, DEVICE_BASE + 3, DEVICE_BASE + 2, DEVICE_BASE + 1));
        query.setTenantId(2L);
        assertPage(mapper.selectCandidatePage(query), 0, List.of());

        TenantContextHolder.setTenantId(2L); session.clearCache();
        query.setTenantId(1L);
        assertPage(mapper.selectCandidatePage(query), 0, List.of());
        query.setTenantId(2L);
        assertPage(mapper.selectCandidatePage(query), 2, List.of(DEVICE_BASE + 9, DEVICE_BASE + 5));
    }

    @Test
    void emptyOrMissingContractScopeUsesOnlyTheProjectBranch() {
        var query = query(); query.setContractNumbers(Set.of());
        assertPage(mapper.selectCandidatePage(query), 3, List.of(DEVICE_BASE + 8, DEVICE_BASE + 3, DEVICE_BASE + 1));
        query.setContractNumbers(null);
        assertPage(mapper.selectCandidatePage(query), 3, List.of(DEVICE_BASE + 8, DEVICE_BASE + 3, DEVICE_BASE + 1));
    }

    @Test
    void missingOrNonpositiveProjectCannotBecomeAContractOnlyOrUnscopedQuery() {
        for (Long missingProject : new Long[] { null, 0L, -1L }) {
            var query = query(); query.setProjectId(missingProject);
            assertPage(mapper.selectCandidatePage(query), 0, List.of());
            // Also exercise fail-closed XML directly, beyond the default method's early return.
            assertEquals(0L, mapper.selectCountCandidates(query));
            assertTrue(mapper.selectPageCandidates(query).isEmpty());
        }
    }

    private static ProjectDeviceSelectionQuery query() {
        var query = new ProjectDeviceSelectionQuery();
        query.setTenantId(1L); query.setProjectId(PROJECT); query.setContractNumbers(Set.of(CONTRACT));
        query.setPageNo(1); query.setPageSize(100);
        return query;
    }

    private static void assertPage(PageResult<DeviceDO> page, long total, List<Long> expectedIds) {
        assertEquals(total, page.getTotal());
        assertEquals(expectedIds, page.getList().stream().map(DeviceDO::getId).toList());
    }

    private void requireExistingTransactionalTable() throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT ENGINE FROM information_schema.tables WHERE table_schema=? AND table_name='ast_device'
                """)) {
            statement.setString(1, SCHEMA);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "测试自身不创建表；必须先由获授权验证步骤准备空 ast_device 结构");
                assertEquals("InnoDB", rows.getString(1), "必须使用支持事务回滚的表结构");
            }
        }
    }

    private record DeviceFixture(int suffix, Long tenantId, Long projectId, String contract,
                                 String serialSuffix, String name, String model, boolean deleted) { }

    private void insert(List<DeviceFixture> fixtures) throws SQLException {
        assertFalse(connection.getAutoCommit(), "隔离 fixture 必须保留在同连接回滚事务内");
        try (var statement = connection.prepareStatement("""
                INSERT INTO ast_device
                  (id,tenant_id,deleted,project_id,customer_id,sn,name,product_code,product_model,contract_no,
                   status,source_system,creator,updater)
                VALUES(?,?,?,?,?,?,?,?,?,?,'IN_SERVICE','MANUAL','inspection-selection-mysql','inspection-selection-mysql')
                """)) {
            for (var fixture : fixtures) {
                statement.setLong(1, DEVICE_BASE + fixture.suffix()); statement.setLong(2, fixture.tenantId());
                statement.setBoolean(3, fixture.deleted()); statement.setLong(4, fixture.projectId());
                statement.setLong(5, CUSTOMER); statement.setString(6, SN_PREFIX + fixture.serialSuffix());
                statement.setString(7, fixture.name()); statement.setString(8, "VERIFY-CODE-" + fixture.suffix());
                statement.setString(9, fixture.model()); statement.setString(10, fixture.contract());
                assertEquals(1, statement.executeUpdate());
            }
        }
        session.clearCache();
    }
}
