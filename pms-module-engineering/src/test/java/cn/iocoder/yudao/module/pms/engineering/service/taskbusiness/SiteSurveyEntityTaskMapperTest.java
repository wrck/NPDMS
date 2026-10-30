package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityAssociationPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Execute the active entity XML: reopened tasks require the actual Owner creation time. */
class SiteSurveyEntityTaskMapperTest {
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 19, 15, 43, 830_000_000);
    private DriverManagerDataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach void setUp() throws Exception {
        TenantContextHolder.setTenantId(1L);
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:entity_survey_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=100", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sol_site_survey (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, "
                    + "name VARCHAR(100), version BIGINT, status INT, deleted INT, confirmed_at TIMESTAMP(3), "
                    + "archived_at TIMESTAMP(3), create_time TIMESTAMP(3))");
            sql.execute("INSERT INTO sol_site_survey (id,tenant_id,project_id,name,version,status,deleted,create_time) VALUES "
                    + "(1,1,100,'archived',4,3,0,'2026-10-20 19:15:43.830'),"
                    + "(2,1,100,'rejected',3,2,0,'2026-10-20 19:15:43.830'),"
                    + "(3,2,100,'foreign tenant',4,3,0,'2026-10-20 19:15:43.830'),"
                    + "(4,1,101,'foreign project',4,3,0,'2026-10-20 19:15:43.830'),"
                    + "(5,1,100,'deleted',4,3,1,'2026-10-20 19:15:43.830')");
        }
        var configuration = new Configuration(new Environment("entity-survey-test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/sitesurvey/entity/SiteSurveyEntityMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, "entity-sitesurvey", configuration.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void activeOwnerProviderReadsCreationTimeForReopenEligibility() {
        try (var session = sessions.openSession()) {
            var provider = new SiteSurveyTaskBusinessObjectProvider(session.getMapper(SiteSurveyEntityMapper.class), null, null, null);
            assertEquals(createdAt, provider.handlingCreatedAt(new AssociationContext(1L, 100L, null, null), "1"));
        }
    }

    @Test void associationAndCompletionProjectionsRetainCreationTimeAndScope() {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(SiteSurveyEntityMapper.class);
            var candidates = mapper.selectAssociationPage(new SiteSurveyEntityAssociationPageQuery(1L, 100L, null, 100));
            assertEquals(List.of(1L), candidates.stream().map(row -> row.getId()).toList());
            assertEquals(createdAt, candidates.getFirst().getCreateTime());
            assertEquals(createdAt, mapper.selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(1L, 100L, 1L)).getCreateTime());
            for (long id : new long[] {3, 4, 5, 999}) {
                var query = new SiteSurveyEntityTaskObjectQuery(1L, 100L, id);
                assertNull(mapper.selectTaskObject(query));
                assertNull(mapper.selectTaskObjectForUpdate(query));
            }
            assertTrue(mapper.selectAssociationPage(new SiteSurveyEntityAssociationPageQuery(9L, 100L, null, 100)).isEmpty());
            session.rollback(true);
        }
    }

    @Test void completionReadStillLocksOwnerRowAgainstConcurrentMutation() throws Exception {
        try (var session = sessions.openSession(false); var concurrent = dataSource.getConnection();
             var update = concurrent.prepareStatement("UPDATE sol_site_survey SET version=5 WHERE id=1")) {
            assertNotNull(session.getMapper(SiteSurveyEntityMapper.class)
                    .selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(1L, 100L, 1L)));
            assertThrows(SQLException.class, update::executeUpdate);
            session.rollback(true);
            assertEquals(1, update.executeUpdate());
        }
    }
}
