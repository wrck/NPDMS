package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmember.OrdinaryProjectMemberService.*;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApiImpl;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** 固定测试库：真实人员过滤、关系SQL、版本/幂等及事务；功能与项目授权边界使用可控替身。 */
@SpringBootTest(classes = OrdinaryProjectMemberMySqlTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class OrdinaryProjectMemberMySqlTest {
    @Resource OrdinaryProjectMemberService service;
    @Resource ActiveUserSelectionApi users;
    @Resource JdbcTemplate jdbc;
    @Resource PermissionCommonApi permissions;
    @Resource ProjectAuthorizationGuard guard;
    @Resource DeptApi departments;
    @Resource ProjectManualCreationService projectReads;
    @Resource ProjectMasterMapper projectMapper;
    // 只替换本组测试不办理的经理命令，避免普通@Bean mock继续触发@Resource真实依赖注入。
    // https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html
    @MockitoBean ProjectManagerMemberApplicationService projectManagers;
    @MockitoBean cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService serviceManagers;
    long projectId, first, second, disabled, foreign, managerAssignment;
    String marker;
    boolean created;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        if (!"npdms_test".equals(required("NPDMS_DB_NAME")) || !"23316".equals(required("NPDMS_MYSQL_PORT")))
            throw new IllegalStateException("Member tests require fixed npdms_test:23316");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
    }

    @BeforeEach void setup() {
        assertEquals("npdms_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        TenantContextHolder.setTenantId(1L);
        reset(permissions, guard, departments, projectReads);
        when(permissions.hasAnyPermissions(anyLong(), eq(OrdinaryProjectMemberService.WRITE_PERMISSION))).thenReturn(true);
        when(departments.getDept(25L)).thenReturn(new DeptRespDTO().setId(25L).setName("测试部门").setCode("MEMBER-DEPT"));
        marker = "member-it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        for (int tenant : new int[]{1, 2}) {
            for (String role : new String[]{"PROJECT_MANAGER", "SALES_REPRESENTATIVE"}) {
                insert("INSERT INTO system_role (name,code,sort,status,type,data_scope,tenant_id,creator) VALUES (?,?,100,0,2,5,?,?)",
                        role, role, tenant, marker);
            }
        }
        first = user("first", 1, 0); second = user("second", 1, 0);
        disabled = user("disabled", 1, 1); foreign = user("foreign", 2, 0);
        projectId = 976_700_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        jdbc.update("""
                INSERT INTO proj_project
                (id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,tree_depth,tree_sort,
                 status,lifecycle_status,current_stage,assignment_status,manager_id,task_tree_version,task_progress_version,version,tenant_id,creator)
                VALUES (?,?,?,0,?,?,'/',0,0,'S2','ACTIVE','S2','UNASSIGNED',?,0,0,0,1,?)
                """, projectId, marker, projectId, marker, projectId, first, marker);
        created = true;
        managerAssignment = insert("INSERT INTO proj_project_member_assignment (project_id,user_id,member_role,assignment_type,effective_from,status,version,tenant_id,creator) VALUES (?,?,'PROJECT_MANAGER','PRIMARY',?,'ACTIVE',0,1,?)",
                projectId, first, LocalDateTime.now().minusDays(1), marker);
        when(projectReads.getProject(eq(projectId), any())).thenAnswer(call -> projectMapper.selectById(projectId));
        when(projectReads.getProjectForManage(eq(projectId), any())).thenAnswer(call -> projectMapper.selectById(projectId));
    }

    @AfterEach void cleanup() {
        if (created) {
            jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=1 AND aggregate_type='Project' AND aggregate_key=? AND operation_code LIKE 'PROJECT_MEMBER_%'", String.valueOf(projectId));
            jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=1 AND scope_code LIKE 'PROJECT_MEMBER_MAINTENANCE:%' AND idempotency_key LIKE ?", marker + "%");
            jdbc.update("DELETE FROM proj_project_member_assignment WHERE tenant_id=1 AND project_id=?", projectId);
            jdbc.update("DELETE FROM proj_project WHERE tenant_id=1 AND id=? AND creator=?", projectId, marker);
        }
        if (marker != null) {
            jdbc.update("DELETE FROM system_user_role WHERE tenant_id IN (1,2) AND creator=?", marker);
            jdbc.update("DELETE FROM system_users WHERE tenant_id IN (1,2) AND creator=?", marker);
            jdbc.update("DELETE FROM system_role WHERE tenant_id IN (1,2) AND creator=?", marker);
        }
        TenantContextHolder.clear();
    }

    @Test void directoryFiltersDisabledDeletedAndForeignUsersWithoutCompanyScopeRows() {
        var query = userQuery(1, 1, marker, null);
        var one = users.page(query);
        var two = users.page(userQuery(2, 1, marker, null));
        assertEquals(2L, one.getTotal()); assertEquals(1, one.getList().size());
        assertNotEquals(one.getList().getFirst().id(), two.getList().getFirst().id());
        assertTrue(users.page(userQuery(1, 20, null, Set.of(foreign, disabled))).getList().isEmpty());
        jdbc.update("UPDATE system_users SET deleted=b'1' WHERE id=? AND creator=?", second, marker);
        assertEquals(1L, users.page(userQuery(1, 20, marker, null)).getTotal());
    }

    @Test void addEditRemoveAndRejoinKeepOldSnapshotsAndReturnScopedHistory() {
        var added = service.mutate(command(Action.ADD, null, first, "TEAM_MEMBER", "原备注", 0, "add"), actor());
        var edited = service.mutate(command(Action.UPDATE, added.assignmentId(), first, "TEAM_MEMBER", "新备注", 1, "edit"), actor());
        assertNotEquals(added.assignmentId(), edited.assignmentId());
        assertEquals("原备注", jdbc.queryForObject("SELECT remark FROM proj_project_member_assignment WHERE id=?", String.class, added.assignmentId()));
        assertEquals("add", jdbc.queryForObject("SELECT change_reason FROM proj_project_member_assignment WHERE id=?", String.class, added.assignmentId()));
        assertEquals("edit", jdbc.queryForObject("SELECT end_reason FROM proj_project_member_assignment WHERE id=?", String.class, added.assignmentId()));
        service.mutate(command(Action.REMOVE, edited.assignmentId(), null, null, null, 2, "remove"), actor());
        service.mutate(command(Action.ADD, null, first, "TEAM_MEMBER", "再次加入", 3, "rejoin"), actor());
        assertEquals(4, version());
        assertEquals(1L, service.page(projectId, new Filter(1, 20, "CURRENT", "TEAM_MEMBER", null), actor()).getTotal());
        assertEquals(2L, service.page(projectId, new Filter(1, 20, "HISTORY", "TEAM_MEMBER", null), actor()).getTotal());
        assertEquals(3L, ordinaryCount());
        assertEquals(first, jdbc.queryForObject("SELECT manager_id FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals("S2", jdbc.queryForObject("SELECT current_stage FROM proj_project WHERE id=?", String.class, projectId));
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=1 AND aggregate_key=?", Long.class, String.valueOf(projectId)));
    }

    @Test void rejectsDuplicateButAllowsSamePersonInDifferentRolesAndProtectsManagers() {
        service.mutate(command(Action.ADD, null, first, "TEAM_MEMBER", "", 0, "add"), actor());
        assertEquals(PROJECT_TEAM_MEMBER_DUPLICATE.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.ADD, null, first, "TEAM_MEMBER", "", 1, "duplicate"), actor())).getCode());
        service.mutate(command(Action.ADD, null, first, "SALES_REPRESENTATIVE", "", 1, "second-role"), actor());
        assertEquals(2L, ordinaryCount());
        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.REMOVE, managerAssignment, null, null, null, 2, "remove-manager"), actor())).getCode());
        assertNull(jdbc.queryForObject("SELECT effective_to FROM proj_project_member_assignment WHERE id=?", LocalDateTime.class, managerAssignment));
    }

    @Test void departmentReadFailureRollsBackClosedIntervalAndIdempotencyReservation() {
        var old = service.mutate(command(Action.ADD, null, first, "TEAM_MEMBER", "原备注", 0, "add"), actor());
        when(departments.getDept(25L)).thenThrow(new IllegalStateException("controlled department unavailability"));
        assertThrows(IllegalStateException.class, () -> service.mutate(command(Action.UPDATE, old.assignmentId(),
                first, "TEAM_MEMBER", "不应保存", 1, "rollback"), actor()));
        assertEquals(1, version()); assertEquals(1L, ordinaryCount());
        assertNull(jdbc.queryForObject("SELECT effective_to FROM proj_project_member_assignment WHERE id=?", LocalDateTime.class, old.assignmentId()));
        assertNull(jdbc.queryForObject("SELECT end_reason FROM proj_project_member_assignment WHERE id=?", String.class, old.assignmentId()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=1 AND idempotency_key=?", Long.class, marker + "-rollback"));
    }

    @Test void replayIsStableEvenAfterUserDisabledAndChangedPayloadConflicts() {
        var command = command(Action.ADD, null, first, "TEAM_MEMBER", "", 0, "retry");
        var saved = service.mutate(command, actor());
        jdbc.update("UPDATE system_users SET status=1 WHERE id=? AND creator=?", first, marker);
        assertEquals(saved, service.mutate(command, actor()));
        assertEquals(1, version()); assertEquals(1L, ordinaryCount());
        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), assertThrows(ServiceException.class, () ->
                service.mutate(command(Action.ADD, null, second, "TEAM_MEMBER", "", 0, "retry"), actor())).getCode());
    }

    @Test void disabledOrForeignUserCannotProduceMembership() {
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.ADD, null, disabled, "TEAM_MEMBER", "", 0, "disabled"), actor()));
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.ADD, null, foreign, "TEAM_MEMBER", "", 0, "foreign"), actor()));
        assertEquals(0, version()); assertEquals(0L, ordinaryCount());
    }

    @Test void concurrentCommandsWithSameProjectVersionHaveOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> race(first, "race-a", start));
            var b = pool.submit(() -> race(second, "race-b", start));
            start.countDown();
            assertEquals(1, a.get(15, TimeUnit.SECONDS) + b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, version()); assertEquals(1L, ordinaryCount());
    }

    private int race(long user, String key, CountDownLatch start) throws Exception {
        start.await(); TenantContextHolder.setTenantId(1L);
        try { service.mutate(command(Action.ADD, null, user, "TEAM_MEMBER", "", 0, key), actor()); return 1; }
        catch (ServiceException conflict) { assertEquals(PROJECT_VERSION_CONFLICT.getCode(), conflict.getCode()); return 0; }
        finally { TenantContextHolder.clear(); }
    }
    private Command command(Action action, Long assignment, Long user, String role, String note, int version, String key) {
        return new Command(projectId, version, action, assignment,
                action == Action.REMOVE ? null : new MemberValues(user, role, "现场支持", note), key, marker + "-" + key);
    }
    private Actor actor() { return new Actor(1L, 7L, marker); }
    private int version() { return jdbc.queryForObject("SELECT version FROM proj_project WHERE id=?", Integer.class, projectId); }
    private long ordinaryCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment WHERE tenant_id=1 AND project_id=? AND member_role IN ('TEAM_MEMBER','SALES_REPRESENTATIVE')", Long.class, projectId); }
    private long user(String suffix, int tenant, int status) {
        long id = insert("INSERT INTO system_users (tenant_id,username,password,nickname,status,dept_id,creator) VALUES (?,?,'',?,?,25,?)", tenant, marker + suffix, suffix, status, marker);
        jdbc.update("INSERT INTO system_user_role (user_id,role_id,tenant_id,creator) SELECT ?,id,tenant_id,? FROM system_role WHERE tenant_id=? AND creator=?",
                id, marker, tenant, marker);
        return id;
    }
    private static ActiveUserSelectionApi.Query userQuery(int page, int size, String keyword, Set<Long> ids) {
        return new ActiveUserSelectionApi.Query(page, size, keyword, ids, "PROJECT_MANAGER", null);
    }
    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]); return statement; }, keys);
        return keys.getKey().longValue();
    }
    private static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + "未配置"); return value; }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual", "cn.iocoder.yudao.module.pms.platform.dal.mysql.command", "cn.iocoder.yudao.module.system.dal.mysql.user"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class, MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class, OrdinaryProjectMemberService.class, ActiveUserSelectionApiImpl.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class Application {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PermissionCommonApi permissions() { return mock(PermissionCommonApi.class); }
        @Bean ProjectAuthorizationGuard guard() { return mock(ProjectAuthorizationGuard.class); }
        @Bean ValidationInitialAssignmentPolicy initialAssignmentPolicy() { return mock(ValidationInitialAssignmentPolicy.class); }
        @Bean DeptApi departments() { return mock(DeptApi.class); }
        @Bean ProjectManualCreationService projectReads() { return mock(ProjectManualCreationService.class); }
    }
}
