package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.UpdateTaskProgressCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTaskLifecycleMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskLifecycleMySqlIntegrationTest {

    private static final String DIGEST = "a".repeat(64);

    @Resource ProjectTaskLifecycleService service;
    @Resource ProjectTaskProgressService progressService;
    @Resource TaskNativeBindingHostProvider nativeProvider;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource DataSource dataSource;
    @Resource InitialTaskReadProbe initialTaskReadProbe;
    @Resource PermissionApi permissionApi;
    @Resource(name = "progressTreeVersionMapper") ProjectTreeVersionMapper projectTreeVersionMapper;

    private long projectId;
    private long taskId;
    private long contractId;
    private long revisionId;
    private String keyPrefix;
    private TaskWorkbenchActor actor;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 979_000_000_000L + seed * 100L;
        taskId = projectId + 11;
        contractId = projectId + 12;
        keyPrefix = "fproj007-task7-it-" + projectId;
        actor = new TaskWorkbenchActor(0L, 9L, keyPrefix + "-trace");
        reset(nativeProvider);
        reset(permissionApi, projectTreeVersionMapper);
        initialTaskReadProbe.reset();
        when(nativeProvider.inspect(any())).thenReturn(new TaskBindingInspection(
                "TASK_NATIVE", Set.of("COMPLETE"), "0:1:1", null));
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(1L);
        when(projectTreeVersionMapper.selectLatestActive(any())).thenReturn(treeVersion);
        insertFixture();
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    statement.executeUpdate("DELETE FROM plt_outbox_event WHERE aggregate_key='" + taskId + "'");
                    statement.executeUpdate("DELETE FROM plt_operation_audit WHERE correlation_id LIKE '"
                            + keyPrefix + "%' ");
                    statement.executeUpdate("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE '"
                            + keyPrefix + "%' ");
                    statement.executeUpdate("DELETE FROM proj_project_task_completion_evaluation WHERE project_task_id="
                            + taskId);
                    statement.executeUpdate("DELETE FROM proj_project_progress_fact WHERE project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE project_task_id="
                            + taskId);
                    statement.executeUpdate("DELETE FROM proj_task_tree_path WHERE project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_task WHERE project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_member_assignment WHERE project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project WHERE id=" + projectId);
                } finally {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET FOREIGN_KEY_CHECKS=1");
                    }
                }
                return null;
            });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void completeAndReplayKeepOneEvaluationAuditAndOutboxInTheCommandTransaction() {
        TaskActionCommand command = new TaskActionCommand(taskId, 0, "complete", null,
                contractId, 1, String.valueOf(taskId), 0L, keyPrefix + "-complete", DIGEST);

        TaskCommandResult completed = service.act(command, actor);
        TaskCommandResult replay = service.act(command, actor);

        assertEquals("DONE", completed.status());
        assertEquals("REPLAY_COMPLETED", replay.replayDecision());
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT status,progress,actual_end_time,version FROM proj_project_task WHERE id=?", taskId);
        assertEquals("DONE", task.get("status"));
        assertEquals(100, ((Number) task.get("progress")).intValue());
        assertNotNull(task.get("actual_end_time"));
        assertEquals(1, ((Number) task.get("version")).intValue());
        assertEquals(1L, count("proj_project_task_completion_evaluation", "project_task_id", taskId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_COMPLETE' AND result_code='SUCCESS'",
                Long.class, actor.correlationId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key=? AND event_type='TaskCompleted'",
                Long.class, String.valueOf(taskId)));
        assertEquals(1L, count("plt_idempotency_record", "idempotency_key", keyPrefix + "-complete"));
    }

    @Test
    void concurrentChildCommittedAfterOldReadViewBlocksCompletionWithCurrentFacts() throws Exception {
        TaskActionCommand command = new TaskActionCommand(taskId, 0, "complete", null,
                contractId, 1, String.valueOf(taskId), 0L, keyPrefix + "-rr-complete", DIGEST);
        long childTaskId = taskId + 20;
        try (var blocker = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            blocker.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            try (var lock = blocker.prepareStatement("SELECT id FROM proj_project WHERE id=? FOR UPDATE")) {
                lock.setLong(1, projectId);
                lock.executeQuery();
            }
            var completion = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    return service.act(command, actor);
                } finally {
                    TenantContextHolder.clear();
                }
            });
            initialTaskReadProbe.awaitRead();
            insertBlockingChild(blocker, childTaskId);
            blocker.commit();

            TaskCommandResult result = completion.get(10, TimeUnit.SECONDS);
            assertEquals("PENDING_ACCEPT", result.status());
        }

        assertEquals("PENDING_ACCEPT", jdbcTemplate.queryForObject(
                "SELECT status FROM proj_project_task WHERE id=?", String.class, taskId));
        assertEquals("NOT_SATISFIED", jdbcTemplate.queryForObject(
                "SELECT evaluation_result_code FROM proj_project_task_completion_evaluation "
                        + "WHERE project_task_id=? AND idempotency_key=?",
                String.class, taskId, keyPrefix + "-rr-complete"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key=? AND event_type='TaskCompleted'",
                Long.class, String.valueOf(taskId)));
    }

    @Test
    void progressUpdateCasAndWeightedFactCommitTogether() {
        long siblingTaskId = taskId + 30;
        jdbcTemplate.update("UPDATE proj_project_task SET status='IN_PROGRESS',progress=20,estimated_hours=2 "
                + "WHERE id=?", taskId);
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,progress,estimated_hours,version,tenant_id) "
                        + "VALUES (?,?,?,?,NULL,?,0,?,'S1',1,'DONE',100,1,0,0)",
                siblingTaskId, projectId, "T-DONE-SIBLING", "已完成兄弟任务", siblingTaskId, revisionId);
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                        + "(id,project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                        + "VALUES (?,?,?,?,0,0,0)", projectId + 31, projectId, siblingTaskId, siblingTaskId);
        jdbcTemplate.update("INSERT INTO proj_project_task_assignment "
                        + "(id,project_task_id,assignee_user_id,effective_from,assigned_by,reason,version,tenant_id) "
                        + "VALUES (?,?,9,?,9,'Task8 progress test',0,0)",
                projectId + 32, taskId, LocalDateTime.now());

        TaskCommandResult result = progressService.updateProgress(new UpdateTaskProgressCommand(taskId, 0, 60), actor);

        assertEquals(1, result.taskVersion());
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT progress,version FROM proj_project_task WHERE id=?", taskId);
        assertEquals(60, ((Number) task.get("progress")).intValue());
        assertEquals(1, ((Number) task.get("version")).intValue());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT task_progress_version FROM proj_project WHERE id=?", Long.class, projectId));
        Map<String, Object> fact = jdbcTemplate.queryForMap(
                "SELECT progress,fact_version,source_watermark FROM proj_project_progress_fact "
                        + "WHERE project_id=? AND fact_source_type='PROJECT_TASK'", projectId);
        assertEquals(new java.math.BigDecimal("73.3333"), fact.get("progress"));
        assertEquals(1L, ((Number) fact.get("fact_version")).longValue());
        assertTrue(String.valueOf(fact.get("source_watermark")).contains("\"participantCount\":2"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_PROGRESS_UPDATE' AND result_code='SUCCESS'",
                Long.class, actor.correlationId()));
    }

    @Test
    void progressFactConflictRollsBackTaskAndProjectVersions() {
        jdbcTemplate.update("UPDATE proj_project_task SET status='IN_PROGRESS',progress=20,estimated_hours=2 "
                + "WHERE id=?", taskId);
        jdbcTemplate.update("INSERT INTO proj_project_task_assignment "
                        + "(id,project_task_id,assignee_user_id,effective_from,assigned_by,reason,version,tenant_id) "
                        + "VALUES (?,?,9,?,9,'Task8 rollback test',0,0)",
                projectId + 42, taskId, LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO proj_project_progress_fact "
                        + "(id,project_id,fact_source_type,fact_source_id,fact_version,progress,source_watermark,"
                        + "occurred_at,version,creator,updater,tenant_id) "
                        + "VALUES (?,?,'PROJECT_TASK',?,1,20,'existing',?,0,'9','9',0)",
                projectId + 43, projectId, String.valueOf(projectId), LocalDateTime.now());

        assertThrows(RuntimeException.class, () -> progressService.updateProgress(
                new UpdateTaskProgressCommand(taskId, 0, 60), actor));

        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT progress,version FROM proj_project_task WHERE id=?", taskId);
        assertEquals(20, ((Number) task.get("progress")).intValue());
        assertEquals(0, ((Number) task.get("version")).intValue());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT task_progress_version FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_progress_fact WHERE project_id=? "
                        + "AND fact_source_type='PROJECT_TASK'", Long.class, projectId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_PROGRESS_UPDATE' AND result_code='SUCCESS'",
                Long.class, actor.correlationId()));
    }

    private void insertBlockingChild(java.sql.Connection connection, long childTaskId) throws Exception {
        try (var insertTask = connection.prepareStatement("INSERT INTO proj_project_task "
                + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                + "state_machine_revision_id,stage_code,sort_order,status,progress,version,tenant_id) "
                + "VALUES (?,?,?,?,?,?,1,?,'S1',1,'PENDING_ASSIGN',0,0,0)")) {
            insertTask.setLong(1, childTaskId);
            insertTask.setLong(2, projectId);
            insertTask.setString(3, "T-BLOCKING-CHILD");
            insertTask.setString(4, "并发新增阻断子任务");
            insertTask.setLong(5, taskId);
            insertTask.setLong(6, taskId);
            insertTask.setLong(7, revisionId);
            insertTask.executeUpdate();
        }
        try (var insertPath = connection.prepareStatement("INSERT INTO proj_task_tree_path "
                + "(id,project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                + "VALUES (?,?,?,?,?,0,0)")) {
            insertPath.setLong(1, projectId + 21);
            insertPath.setLong(2, projectId);
            insertPath.setLong(3, taskId);
            insertPath.setLong(4, childTaskId);
            insertPath.setInt(5, 1);
            insertPath.executeUpdate();
            insertPath.setLong(1, projectId + 22);
            insertPath.setLong(3, childTaskId);
            insertPath.setInt(5, 0);
            insertPath.executeUpdate();
        }
    }

    private void insertFixture() {
        revisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0)",
                projectId, "F007-T7-" + projectId, projectId, 0,
                "F-PROJ-007 Task7 " + projectId, projectId, "/", 0, 0);
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,progress,version,tenant_id) "
                        + "VALUES (?,?,?,?,NULL,?,0,?,'S1',0,'PENDING_ACCEPT',99,0,0)",
                taskId, projectId, "T-COMPLETE", "完成判定任务", taskId, revisionId);
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                        + "(id,project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                        + "VALUES (?,?,?,?,0,0,0)", projectId + 13, projectId, taskId, taskId);
        jdbcTemplate.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,project_task_id,work_binding_type_code,binding_parameter_snapshot,permission_policy_ref,"
                        + "completion_rule_type_code,completion_rule_snapshot,source_definition_version,contract_version,"
                        + "effective_from,version,creator,updater,tenant_id) "
                        + "VALUES (?,?,'TASK_NATIVE','{\"schemaVersion\":1}','PROJECT_TASK_NATIVE_DEFAULT',"
                        + "'TASK_NATIVE_STATUS','{\"schemaVersion\":1,\"requiredStatus\":\"DONE\"}',1,1,?,0,'9','9',0)",
                contractId, taskId, LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(id,project_id,user_id,member_role,status,version,tenant_id) "
                        + "VALUES (?,?,9,'PROJECT_MANAGER','ACTIVE',0,0)", projectId + 14, projectId);
    }

    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformCommandExecutionApiImpl.class,
            OperationAuditApiImpl.class, ProjectTaskLifecycleService.class, ProjectTaskProgressService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TaskNativeBindingHostProvider nativeProvider() { return mock(TaskNativeBindingHostProvider.class); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectTreeScopeService treeScopeService() { return mock(ProjectTreeScopeService.class); }
        @Bean @Primary ProjectTreeVersionMapper progressTreeVersionMapper() {
            return mock(ProjectTreeVersionMapper.class);
        }
        @Bean InitialTaskReadProbe initialTaskReadProbe() { return new InitialTaskReadProbe(); }
        @Bean
        @Primary
        ProjectTaskRuntimeMapper probedTaskMapper(
                @Qualifier("projectTaskRuntimeMapper") ProjectTaskRuntimeMapper delegate,
                InitialTaskReadProbe probe) {
            return (ProjectTaskRuntimeMapper) Proxy.newProxyInstance(
                    ProjectTaskRuntimeMapper.class.getClassLoader(),
                    new Class<?>[]{ProjectTaskRuntimeMapper.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if ("selectTask".equals(method.getName())) probe.taskRead();
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    static final class InitialTaskReadProbe {
        private volatile CountDownLatch read = new CountDownLatch(1);
        void reset() { read = new CountDownLatch(1); }
        void taskRead() { read.countDown(); }
        void awaitRead() throws InterruptedException {
            if (!read.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("任务初始读未发生");
        }
    }
}
