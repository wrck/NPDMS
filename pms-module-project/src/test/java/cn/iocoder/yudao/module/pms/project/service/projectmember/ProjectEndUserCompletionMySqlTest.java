package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real Mapper SQL over connection-local temporary tables; shared business rows are never written. */
@EnabledIfSystemProperty(named = "npdms.end-user.mysql", matches = "true")
class ProjectEndUserCompletionMySqlTest {
    Connection db;
    SqlSession session;
    ProjectAssignmentCompletionMapper mapper;
    ProjectEndUserCompletionService service;

    @BeforeEach void setup() throws Exception {
        assertEquals("24306", System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test", System.getenv("NPDMS_DB_NAME"));
        db = DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"));
        try (var sql = db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE proj_project (id BIGINT PRIMARY KEY, tenant_id BIGINT, deleted BIT)");
            sql.execute("INSERT INTO proj_project VALUES (9,7,b'0')");
            sql.execute("CREATE TEMPORARY TABLE proj_project_member_assignment (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, user_id BIGINT, member_role VARCHAR(30), member_name VARCHAR(100), status VARCHAR(20), authorization_confirmed BIT, deleted BIT, effective_from DATETIME, effective_to DATETIME, version INT)");
        }
        db.setAutoCommit(false);
        var config = new Configuration(new Environment("end-user-test", new JdbcTransactionFactory(), new SingleConnectionDataSource(db, true)));
        config.setMapUnderscoreToCamelCase(true);
        try (var xml = getClass().getResourceAsStream("/mapper/completion/ProjectAssignmentCompletionMapper.xml")) {
            new XMLMapperBuilder(xml, config, "ProjectAssignmentCompletionMapper", config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        mapper = session.getMapper(ProjectAssignmentCompletionMapper.class);
        service = new ProjectEndUserCompletionService(mapper);
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach void close() throws Exception {
        TenantContextHolder.clear();
        if (session != null) session.close();
        if (db != null) db.close();
    }

    @Test void unconfirmedExternalEndUserCompletesWithoutWeakeningAssignmentAuthorization() throws Exception {
        try (var sql = db.createStatement()) {
            sql.execute("INSERT INTO proj_project_member_assignment VALUES (1,7,9,NULL,'END_USER','External','ACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,2),(2,7,9,100,'PROJECT_MANAGER','Pending','ACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,0),(3,7,9,101,'PROJECT_MANAGER','Confirmed','ACTIVE',b'1',b'0',NOW()-INTERVAL 1 DAY,NULL,0)");
        }
        var fact = service.lockAndInspect(new ProjectEndUserCompletionService.Query(7L, 9L));
        assertEquals(List.of(new ProjectEndUserCompletionService.MemberFact(1L, 2)), fact.members());
        assertTrue(fact.completed());
        assertEquals(List.of(3L), mapper.members(new ProjectAssignmentCompletionQuery(7L, 9L, true)).stream().map(member -> member.getId()).toList());
    }

    @Test void foreignDeletedExpiredFutureInactiveAndAccountMembersCannotComplete() throws Exception {
        try (var sql = db.createStatement()) {
            sql.execute("INSERT INTO proj_project_member_assignment VALUES (1,8,9,NULL,'END_USER','Other tenant','ACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,0),(2,7,10,NULL,'END_USER','Other project','ACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,0),(3,7,9,NULL,'END_USER','Deleted','ACTIVE',b'0',b'1',NOW()-INTERVAL 1 DAY,NULL,0),(4,7,9,NULL,'END_USER','Expired','ACTIVE',b'0',b'0',NOW()-INTERVAL 2 DAY,NOW()-INTERVAL 1 DAY,0),(5,7,9,NULL,'END_USER','Future','ACTIVE',b'0',b'0',NOW()+INTERVAL 1 DAY,NULL,0),(6,7,9,NULL,'END_USER','Inactive','INACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,0),(7,7,9,100,'END_USER','Account','ACTIVE',b'0',b'0',NOW()-INTERVAL 1 DAY,NULL,0)");
        }
        assertEquals(List.of(4L, 5L, 6L, 7L), mapper.endUserMembers(new ProjectAssignmentCompletionQuery(7L, 9L, true)).stream().map(member -> member.getId()).toList());
        assertFalse(service.lockAndInspect(new ProjectEndUserCompletionService.Query(7L, 9L)).completed());
    }

    @Test void frozenEndUserTimeSurvivesIntervalClosureWithoutUsingReplacementOrAuthorization() throws Exception {
        try (var sql = db.createStatement()) {
            sql.execute("INSERT INTO proj_project_member_assignment VALUES (1,7,9,NULL,'END_USER','Original','INACTIVE',b'0',b'0','2026-10-20 09:00:00','2026-10-23 09:00:00',2),(2,7,9,NULL,'END_USER','Replacement','ACTIVE',b'0',b'0','2026-10-24 09:00:00',NULL,0)");
        }
        var projection = new cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectExecutionTimeProjection(
                null, mapper, null, List.of());
        var round = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO();
        round.setId(31L); round.setTenantId(7L); round.setProjectId(9L); round.setNodeKind("TASK");
        round.setStatus("DONE"); round.setEndedAt(java.time.LocalDateTime.of(2026, 10, 21, 12, 0));
        round.setResultSnapshot("{\"projectEndUsers\":{\"projectId\":9,\"members\":[{\"assignmentId\":1}]}}");
        var query = new cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectExecutionTimeProjection.Query(
                7L, 9L, 100L, round, new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot(), List.of(round));
        assertEquals(java.time.LocalDateTime.of(2026, 10, 20, 9, 0), projection.project(query).at());
        assertTrue(mapper.members(new ProjectAssignmentCompletionQuery(7L, 9L, false)).isEmpty());
        assertNull(round.getSubmittedAt());
        assertEquals("DONE", round.getStatus());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 21, 12, 0), round.getEndedAt());
    }
}
