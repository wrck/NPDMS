package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Read-only paging against the task containing the real, large correction definition. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql", matches="true")
class SyncTaskPageMySqlTest {
    SqlSession session;
    Connection db;
    SyncTaskMapper mapper;

    @BeforeEach void setup() throws Exception {
        assertEquals("24306", System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test", System.getenv("NPDMS_DB_NAME"));
        var source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/sync/SyncTaskMapper.xml"));
        db = source.getConnection();
        db.setReadOnly(true);
        db.setAutoCommit(false);
        session = factory.getObject().openSession(db);
        mapper = session.getMapper(SyncTaskMapper.class);
    }

    @AfterEach void close() throws Exception {
        if (session != null) session.close();
        if (db != null && !db.isClosed()) db.close();
    }

    private SyncQueries.Page page(long tenant, int number, int size) {
        var query = new SyncQueries.Page();
        query.setTenantId(tenant);query.setPageNo(number);query.setPageSize(size);
        return query;
    }

    private List<Long> expectedIds(long tenant) throws Exception {
        var ids = new ArrayList<Long>();
        try (var sql = db.prepareStatement("SELECT id FROM int_sync_task WHERE tenant_id=? AND deleted=FALSE ORDER BY id DESC")) {
            sql.setLong(1, tenant);
            try (var result = sql.executeQuery()) { while (result.next()) ids.add(result.getLong(1)); }
        }
        return ids;
    }

    @Test void largeDefinitionsPageWithoutFilesortMemoryFailureOrMissingRows() throws Exception {
        var expected = expectedIds(1);
        assertFalse(expected.isEmpty());
        var first = mapper.selectPage(page(1, 1, 200));
        assertEquals((long) expected.size(), first.getTotal());
        assertEquals(expected.subList(0, Math.min(200, expected.size())), first.getList().stream().map(x -> x.getId()).toList());
        assertTrue(first.getList().stream().anyMatch(x -> x.getDefinition().length() > 1_000_000),
                "The large definition that triggered the runtime failure must be exercised");
        var all = new ArrayList<Long>();
        for (int number = 1; all.size() < expected.size(); number++) {
            var result = mapper.selectPage(page(1, number, 3));
            assertEquals((long) expected.size(), result.getTotal());
            assertFalse(result.getList().isEmpty());
            all.addAll(result.getList().stream().map(x -> x.getId()).toList());
        }
        assertEquals(expected, all);
        var beyond = mapper.selectPage(page(1, expected.size() + 1, 3));
        assertEquals((long) expected.size(), beyond.getTotal());assertTrue(beyond.getList().isEmpty());
    }

    @Test void tenantDeletedAndBoundedPageRulesRemainEffective() throws Exception {
        var expected = expectedIds(2);
        var result = mapper.selectPage(page(2, 1, 200));
        assertEquals((long) expected.size(), result.getTotal());
        assertEquals(expected.subList(0, Math.min(200, expected.size())), result.getList().stream().map(x -> x.getId()).toList());
        assertTrue(result.getList().stream().allMatch(x -> x.getTenantId() == 2L && !x.getDeleted()));
        var query = page(1, 1, 10);query.setTenantId(null);
        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(query));
        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(page(1, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(page(1, 1, -1)));
        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(page(1, 1, 201)));
    }
}
