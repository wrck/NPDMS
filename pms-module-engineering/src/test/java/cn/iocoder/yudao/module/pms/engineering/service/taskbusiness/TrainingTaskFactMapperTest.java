package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingTaskFactMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskPageQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.h2.jdbcx.JdbcDataSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real wrapper and XML projections in an isolated database, without customer secrets. */
class TrainingTaskFactMapperTest {
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 22, 9, 18, 30);
    private JdbcDataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:training_fact_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=100");
        dataSource.setUser("sa"); dataSource.setPassword("");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE imp_eng_training (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, "
                    + "name VARCHAR(100), status INT, version BIGINT, deleted INT, create_time TIMESTAMP(3))");
            sql.execute("INSERT INTO imp_eng_training VALUES "
                    + "(37,1,191,'confirmed',2,2,0,'2026-10-22 09:18:30'),"
                    + "(38,1,191,'draft',0,1,0,'2026-10-22 09:18:30'),"
                    + "(39,2,191,'foreign tenant',2,2,0,'2026-10-22 09:18:30'),"
                    + "(40,1,192,'foreign project',2,2,0,'2026-10-22 09:18:30'),"
                    + "(41,1,191,'deleted',2,2,1,'2026-10-22 09:18:30')");
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("training-facts", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);
        try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/training/TrainingTaskFactMapper.xml")) {
            assertNotNull(xml);
            // H2 does not parse MySQL bit literals. Only normalize that literal; retain the real
            // projection, tenant/project/deleted predicates and FOR UPDATE lock from the Owner XML.
            var compatible = new String(xml.readAllBytes(), StandardCharsets.UTF_8).replace("b'0'", "0");
            new XMLMapperBuilder(new ByteArrayInputStream(compatible.getBytes(StandardCharsets.UTF_8)),
                    configuration, "training-facts", configuration.getSqlFragments()).parse();
        }
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void ownerCreationTimeIsReadByTheRealMapperRatherThanSuppliedByATestDouble() {
        try (var session = sessions.openSession()) {
            var provider = new TrainingTaskBusinessObjectProvider(session.getMapper(TrainingTaskFactMapper.class), null, null, null);
            assertEquals(createdAt, provider.handlingCreatedAt(new AssociationContext(1L, 191L, "PROJECT_TRAINING", "{}"), "37"));
        }
    }

    @Test void candidateAndLockedFactsPreserveScopeCreationTimeAndActualConfirmationState() {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(TrainingTaskFactMapper.class);
            var candidates = mapper.selectAssociationPage(new TrainingTaskPageQuery(1L, 191L, null, 100));
            assertEquals(List.of(37L, 38L), candidates.stream().map(row -> row.getId()).toList());
            assertTrue(candidates.stream().allMatch(row -> createdAt.equals(row.getCreateTime())));
            var confirmed = mapper.selectTaskFactForUpdate(new TrainingTaskFactQuery(1L, 191L, 37L));
            assertEquals(2, confirmed.getStatus()); assertEquals(createdAt, confirmed.getCreateTime());
            assertEquals(0, mapper.selectTaskFact(new TrainingTaskFactQuery(1L, 191L, 38L)).getStatus());
            for (long id : new long[]{39, 40, 41, 999}) {
                assertNull(mapper.selectTaskFact(new TrainingTaskFactQuery(1L, 191L, id)));
                assertNull(mapper.selectTaskFactForUpdate(new TrainingTaskFactQuery(1L, 191L, id)));
            }
            session.rollback(true);
        }
    }

    @Test void completionReadKeepsTheOwnerLockUntilTheTransactionEnds() throws Exception {
        try (var session = sessions.openSession(false); var concurrent = dataSource.getConnection();
             var update = concurrent.prepareStatement("UPDATE imp_eng_training SET version=3 WHERE id=37")) {
            assertNotNull(session.getMapper(TrainingTaskFactMapper.class)
                    .selectTaskFactForUpdate(new TrainingTaskFactQuery(1L, 191L, 37L)));
            assertThrows(SQLException.class, update::executeUpdate);
            session.rollback(true);
            assertEquals(1, update.executeUpdate());
        }
    }
}
