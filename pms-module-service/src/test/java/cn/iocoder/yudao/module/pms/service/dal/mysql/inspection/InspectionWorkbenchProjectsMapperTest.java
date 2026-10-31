package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionWorkbenchProjectsQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionWorkbenchProjectsMapperTest {
    private Connection connection;
    private SqlSession session;

    @BeforeEach
    void createIsolatedCandidateFixtures() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE srv_inspection_task (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, deleted INT)");
            statement.execute("INSERT INTO srv_inspection_task VALUES (1,7,101,0),(2,7,101,0),(3,7,102,1),(4,8,103,0),(5,7,104,0)");
        }
        TenantContextHolder.setTenantId(7L);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setEnvironment(new Environment("inspection-candidates", new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(InspectionTaskMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
    }

    @AfterEach
    void closeIsolatedDatabase() throws Exception {
        try {
            if (session != null) session.close();
        } finally {
            try { if (connection != null) connection.close(); }
            finally { TenantContextHolder.clear(); }
        }
    }

    @Test
    void candidatesDeduplicateProjectsAndExcludeDeletedTasksAndOtherTenants() {
        var mapper = session.getMapper(InspectionTaskMapper.class);
        assertEquals(Set.of(101L, 104L), Set.copyOf(mapper.selectWorkbenchProjectIds(new InspectionWorkbenchProjectsQuery(7L))));
    }

    @Test
    void explicitTenantCannotOverrideAuthenticatedTenant() {
        var mapper = session.getMapper(InspectionTaskMapper.class);
        assertTrue(mapper.selectWorkbenchProjectIds(new InspectionWorkbenchProjectsQuery(8L)).isEmpty());
    }
}
