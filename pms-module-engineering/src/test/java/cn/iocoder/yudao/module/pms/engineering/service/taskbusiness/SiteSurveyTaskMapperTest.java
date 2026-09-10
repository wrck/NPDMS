package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskCandidateQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskObjectQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** REQ-PROJ-004: execute real Owner XML against an isolated in-memory database (not MySQL acceptance). */
class SiteSurveyTaskMapperTest {
    private DriverManagerDataSource dataSource;
    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:survey_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=100", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE pms_eng_site_survey (id BIGINT PRIMARY KEY, tenant_id BIGINT, "
                    + "project_id BIGINT, name VARCHAR(100), version INT, status INT, deleted INT)");
            for (int id = 1; id <= 105; id++) {
                sql.execute("INSERT INTO pms_eng_site_survey VALUES (" + id + ",3,100,'survey',7,1,0)");
            }
            sql.execute("INSERT INTO pms_eng_site_survey VALUES (201,4,100,'foreign tenant',7,1,0),"
                    + "(202,3,101,'foreign project',7,1,0),(203,3,100,'deleted',7,1,1)");
        }
        Configuration configuration = new Configuration(new Environment("survey-test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/sitesurvey/SiteSurveyMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, "sitesurvey", configuration.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void candidatesFilterTenantProjectDeletedAndApplyStableBound() {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(SiteSurveyMapper.class);
            var candidates = mapper.selectTaskCandidates(new SiteSurveyTaskCandidateQuery(3L, 100L, 100));
            assertEquals(100, candidates.size());
            assertEquals(105L, candidates.getFirst().getId());
            assertEquals(6L, candidates.getLast().getId());
            assertTrue(candidates.stream().allMatch(row -> row.getTenantId() == 3 && row.getProjectId() == 100));
            assertTrue(mapper.selectTaskCandidates(new SiteSurveyTaskCandidateQuery(9L, 100L, 100)).isEmpty());
        }
    }

    @Test
    void inspectAndLockBothRejectForeignOrDeletedObjects() {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(SiteSurveyMapper.class);
            for (long id : new long[] {201, 202, 203, 999}) {
                var query = new SiteSurveyTaskObjectQuery(3L, 100L, id);
                assertNull(mapper.selectTaskObject(query));
                assertNull(mapper.selectTaskObjectForUpdate(query));
            }
            var row = mapper.selectTaskObject(new SiteSurveyTaskObjectQuery(3L, 100L, 1L));
            assertEquals(7, row.getVersion());
            assertEquals(1, row.getStatus());
            session.rollback(true);
        }
    }

    @Test
    void ownerLockBlocksConcurrentOriginalRowMutationUntilTransactionEnds() throws Exception {
        try (var session = sessions.openSession(false); var concurrent = dataSource.getConnection();
             var update = concurrent.prepareStatement("UPDATE pms_eng_site_survey SET version=8 WHERE id=1")) {
            assertNotNull(session.getMapper(SiteSurveyMapper.class)
                    .selectTaskObjectForUpdate(new SiteSurveyTaskObjectQuery(3L, 100L, 1L)));
            assertThrows(SQLException.class, update::executeUpdate);
            session.rollback(true);
            assertEquals(1, update.executeUpdate());
        }
    }
}
