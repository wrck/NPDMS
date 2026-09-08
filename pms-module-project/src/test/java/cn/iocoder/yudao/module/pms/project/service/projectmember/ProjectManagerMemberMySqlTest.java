package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApiImpl;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-01：固定测试库，真实资格/事务/审计/Outbox；权限接口用可控边界验证拒绝。 */
@SpringBootTest(classes = ProjectManagerMemberMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectManagerMemberMySqlTest {
    @Resource ProjectManagerMemberApplicationService service;
    @Resource ProjectParticipantFactApiImpl participants;
    @Resource JdbcTemplate jdbc;
    @Resource PermissionCommonApi permissions;
    @Resource ProjectAuthorizationGuard scopeGuard;
    @Resource FailingOutbox outbox;
    long projectId, companyId, first, second;
    String marker;
    boolean created;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
    }

    @BeforeEach
    void setup() {
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        TenantContextHolder.setTenantId(0L);
        reset(permissions, scopeGuard);
        when(permissions.hasAnyPermissions(anyLong(), any())).thenReturn(true);
        outbox.setFail(false);
        marker = "pm01m-" + UUID.randomUUID().toString().substring(0, 10);
        created = false;
        companyId = insert("INSERT INTO system_company (tenant_id,code,name,status,version,creator) VALUES (0,?,?,0,0,?)",
                marker, marker, marker);
        first = user("first"); second = user("second");
        qualify(first); qualify(second);
        projectId = 976_500_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        jdbc.update("""
                INSERT INTO proj_project
                (id,project_code,code_root_id,project_sequence,project_name,company_id,company_code,company_name,
                 root_id,tree_path,tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,
                 task_tree_version,task_progress_version,version,tenant_id,creator)
                VALUES (?,?,?,0,?,?,?,?,?,'/',0,0,'S0','ACTIVE','S2','UNASSIGNED',0,0,0,0,?)
                """, projectId, marker, projectId, marker, companyId, marker, marker, projectId, marker);
        created = true;
        jdbc.update("INSERT INTO proj_project_member_assignment (project_id,user_id,member_role,assignment_type,"
                + "effective_from,status,version,tenant_id,creator) VALUES (?,7,'SERVICE_MANAGER_L1','PRIMARY',?,'ACTIVE',0,0,?)",
                projectId, LocalDateTime.now().minusDays(1), marker);
    }

    @AfterEach
    void cleanup() {
        outbox.setFail(false);
        if (created) {
            jdbc.update("DELETE FROM plt_outbox_event WHERE aggregate_type='Project' AND aggregate_key=? "
                    + "AND event_type='ProjectManagersChanged'", String.valueOf(projectId));
            jdbc.update("DELETE FROM plt_operation_audit WHERE aggregate_type='Project' AND aggregate_key=? "
                    + "AND operation_code='PROJECT_MANAGERS_UPDATE'", String.valueOf(projectId));
            jdbc.update("DELETE FROM plt_idempotency_record WHERE resource_type='Project' AND resource_key=?",
                    String.valueOf(projectId));
            jdbc.update("DELETE FROM proj_project_member_assignment WHERE tenant_id=0 AND project_id=?", projectId);
            jdbc.update("DELETE FROM proj_project WHERE tenant_id=0 AND id=? AND creator=?", projectId, marker);
        }
        if (marker != null) {
            jdbc.update("DELETE FROM system_user_company_department_scope WHERE creator=?", marker);
            jdbc.update("DELETE FROM system_users WHERE creator=?", marker);
            jdbc.update("DELETE FROM system_company WHERE creator=?", marker);
        }
        TenantContextHolder.clear();
    }

    @Test
    void addsMultipleManagersAndChangesPrimaryWithoutRewritingMembershipHistory() {
        jdbc.update("UPDATE proj_project SET manager_employee_no='OLD-EMP' WHERE id=?", projectId);
        var added = service.update(command(0, Set.of(first, second), Set.of(), first, "add"), actor());
        assertNull(jdbc.queryForObject("SELECT manager_employee_no FROM proj_project WHERE id=?", String.class, projectId));
        assertEquals(2, added.members().size());
        assertEquals(1, added.version());
        assertEquals("ASSIGNED", added.assignmentStatus());
        assertEquals(second, participants.inspect(new ProjectParticipantFactQuery(projectId, second,
                Set.of("PROJECT_MANAGER"), LocalDateTime.now())).userId());
        var switched = service.update(command(1, Set.of(), Set.of(), second, "switch"), actor());
        assertEquals(second, switched.primaryUserId());
        assertEquals(added.members(), switched.members());
        assertEquals("second", jdbc.queryForObject("SELECT manager_name FROM proj_project WHERE id=?", String.class, projectId));
        assertEquals("S2", jdbc.queryForObject("SELECT current_stage FROM proj_project WHERE id=?", String.class, projectId));
        var removed = service.update(command(2, Set.of(), Set.of(first), null, "remove"), actor());
        assertEquals(List.of(second), removed.members().stream().map(ProjectManagerMemberResult.Member::userId).toList());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment "
                + "WHERE project_id=? AND user_id=? AND effective_to IS NOT NULL", Long.class, projectId, first));
        var cleared = service.update(command(3, Set.of(), Set.of(second), null, "clear"), actor());
        assertNull(cleared.primaryUserId());
        assertEquals("UNASSIGNED", cleared.assignmentStatus());
        assertNull(jdbc.queryForObject("SELECT manager_name FROM proj_project WHERE id=?", String.class, projectId));
    }

    @Test
    void replayIsStableAndNoopDoesNotAdvanceVersionOrPublishChangedEvent() {
        var cmd = command(0, Set.of(first), Set.of(), first, "replay");
        var result = service.update(cmd, actor());
        assertEquals(result, service.update(cmd, actor()));
        assertEquals(1L, count("plt_outbox_event"));
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(second), Set.of(), second, "replay"), actor()));
        var noop = service.update(command(1, Set.of(first), Set.of(), first, "noop"), actor());
        assertFalse(noop.changed());
        assertEquals(1, noop.version());
        assertEquals(1L, count("plt_outbox_event"));
    }

    @Test
    void rejectsMissingQualificationAndInvalidPrimaryWithoutWrites() {
        jdbc.update("UPDATE system_user_company_department_scope SET status=1 WHERE user_id=? AND creator=?", second, marker);
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first, second), Set.of(), first, "bad-user"), actor()));
        assertEquals(0, version());
        assertEquals(0L, managerCount());
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), second, "bad-primary"), actor()));
        assertEquals(0L, count("plt_operation_audit"));
    }

    @Test
    void authorizationLifecycleAndVersionFailuresLeaveFactsUnchanged() {
        when(permissions.hasAnyPermissions(anyLong(), any())).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), first, "permission"), actor()));
        when(permissions.hasAnyPermissions(anyLong(), any())).thenReturn(true);
        doThrow(new IllegalStateException("denied scope")).when(scopeGuard).assertCanAssign(any(), eq(projectId));
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), first, "scope"), actor()));
        reset(scopeGuard);
        assertThrows(RuntimeException.class, () -> service.update(command(3, Set.of(first), Set.of(), first, "version"), actor()));
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), first, "tenant"),
                new ProjectManagerMemberApplicationService.Actor(1L, 7L, marker)));
        jdbc.update("UPDATE proj_project SET lifecycle_status='NORMAL_CLOSED' WHERE id=?", projectId);
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), first, "closed"), actor()));
        assertEquals(0, version());
        assertEquals(0L, managerCount());
        assertEquals(0L, count("plt_operation_audit"));
    }

    @Test
    void outboxFailureRollsBackMembersProjectionAuditAndIdempotency() {
        outbox.setFail(true);
        assertThrows(RuntimeException.class, () -> service.update(command(0, Set.of(first), Set.of(), first, "rollback"), actor()));
        assertEquals(0, version());
        assertEquals(0L, managerCount());
        assertEquals(0L, count("plt_operation_audit"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key=?",
                Long.class, marker + "-rollback"));
        outbox.setFail(false);
        assertTrue(service.update(command(0, Set.of(first), Set.of(), first, "rollback"), actor()).changed());
    }

    @Test
    void concurrentCommandsWithSameVersionHaveOnlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> concurrentAdd(first, "race-a", start));
            var two = pool.submit(() -> concurrentAdd(second, "race-b", start));
            start.countDown();
            assertEquals(1, one.get(15, TimeUnit.SECONDS) + two.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, version());
        assertEquals(1L, managerCount());
        assertEquals(1L, count("plt_outbox_event"));
    }

    private int concurrentAdd(long userId, String key, CountDownLatch start) throws Exception {
        start.await();
        TenantContextHolder.setTenantId(0L);
        try { service.update(command(0, Set.of(userId), Set.of(), userId, key), actor()); return 1; }
        catch (cn.iocoder.yudao.framework.common.exception.ServiceException conflict) { return 0; }
        finally { TenantContextHolder.clear(); }
    }

    private ProjectManagerMemberCommand command(int version, Set<Long> add, Set<Long> remove, Long primary, String key) {
        return new ProjectManagerMemberCommand(projectId, version, add, remove, primary, "人员调整", marker + "-" + key);
    }
    private ProjectManagerMemberApplicationService.Actor actor() { return new ProjectManagerMemberApplicationService.Actor(0L, 7L, marker); }
    private int version() { return jdbc.queryForObject("SELECT version FROM proj_project WHERE id=?", Integer.class, projectId); }
    private long managerCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment WHERE project_id=? AND member_role='PROJECT_MANAGER'", Long.class, projectId); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE aggregate_type='Project' AND aggregate_key=?", Long.class, String.valueOf(projectId)); }
    private long user(String name) { return insert("INSERT INTO system_users (tenant_id,username,password,nickname,status,creator) VALUES (0,?,'',?,0,?)", marker + name, name, marker); }
    private void qualify(long user) { jdbc.update("INSERT INTO system_user_company_department_scope (tenant_id,user_id,company_id,company_code,company_name,scope_role,is_primary,effective_from,status,version,creator) VALUES (0,?,?,?,?,'PROJECT_MANAGER',FALSE,?,0,0,?)", user, companyId, marker, marker, LocalDateTime.now().minusDays(1), marker); }
    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var s = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); return s; }, keys);
        return keys.getKey().longValue();
    }
    private static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + "未配置"); return value; }

    static class FailingOutbox extends PlatformTransactionalOutboxWriter {
        volatile boolean fail;
        public void setFail(boolean value) { fail = value; }
        FailingOutbox(PlatformOutboxEventMapper mapper) { super(mapper); }
        @Override public void write(Long tenant, PlatformCommandExecutionApi.BusinessEvent event,
                String type, String key, LocalDateTime at) {
            if (fail) throw new IllegalStateException("controlled Outbox failure");
            super.write(tenant, event, type, key, at);
        }
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.system.dal.mysql.permission", "cn.iocoder.yudao.module.system.dal.mysql.company",
            "cn.iocoder.yudao.module.system.dal.mysql.dept"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class, MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class, ProjectManagerMemberApplicationService.class, ProjectCreationAuthorizationService.class,
            ProjectParticipantFactApiImpl.class, OrganizationScopeApiImpl.class, PlatformCommandExecutionApiImpl.class})
    static class Application {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PermissionCommonApi permissions() { return mock(PermissionCommonApi.class); }
        @Bean ProjectAuthorizationGuard scopeGuard() { return mock(ProjectAuthorizationGuard.class); }
        @Bean FailingOutbox outbox(PlatformOutboxEventMapper mapper) { return new FailingOutbox(mapper); }
    }
}
