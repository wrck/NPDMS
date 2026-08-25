package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AddDependencyCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.CreateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
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
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTaskCommandMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskCommandMySqlIntegrationTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    @Resource ProjectTaskCommandService service;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource ContractInsertFault contractInsertFault;
    @Resource PermissionApi permissionApi;

    private long projectId;
    private long publishedRevisionId;
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
        projectId = 978_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        keyPrefix = "fproj007-task5-it-" + projectId;
        actor = new TaskWorkbenchActor(0L, 9L, keyPrefix + "-trace");
        contractInsertFault.enabled = false;
        reset(permissionApi);
        when(permissionApi.hasAnyPermissions(any(), any())).thenReturn(true);
        publishedRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        insertProjectFixture();
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    statement.executeUpdate("DELETE FROM plt_operation_audit WHERE correlation_id LIKE '"
                            + keyPrefix + "%'");
                    statement.executeUpdate("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE '"
                            + keyPrefix + "%'");
                    statement.executeUpdate("DELETE FROM proj_task_dependency WHERE tenant_id=0 AND project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_progress_fact WHERE tenant_id=0 AND project_id="
                            + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE tenant_id=0 "
                            + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + projectId + ")");
                    statement.executeUpdate("DELETE FROM proj_task_tree_path WHERE tenant_id=0 AND project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_member_assignment WHERE tenant_id=0 AND project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_stage WHERE tenant_id=0 AND project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_tree_version WHERE tenant_id=0 AND root_project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + projectId);
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
    void createReplaysSameKeyConflictsDifferentPayloadAndWritesOneSuccessFact() {
        CreateTaskCommand first = createCommand("T-1", keyPrefix + "-create", DIGEST_A);

        TaskCommandResult created = service.create(first, actor);
        TaskCommandResult replay = service.create(first, actor);
        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.create(createCommand("T-1", keyPrefix + "-create", DIGEST_B), actor));

        assertEquals("NEW", created.replayDecision());
        assertEquals("REPLAY_COMPLETED", replay.replayDecision());
        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), conflict.getCode());
        assertEquals(1L, count("proj_project_task", "project_id=?", projectId));
        assertEquals(1L, count("proj_task_tree_path", "project_id=?", projectId));
        assertEquals(1L, count("proj_project_task_execution_contract", "project_task_id=?", created.taskId()));
        assertEquals(1L, count("plt_idempotency_record", "idempotency_key=?", keyPrefix + "-create"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_CREATE' AND result_code='SUCCESS'",
                Long.class, actor.correlationId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_CREATE' AND result_code='REJECTED'",
                Long.class, actor.correlationId()));
        String detail = jdbcTemplate.queryForObject(
                "SELECT detail_snapshot FROM plt_operation_audit WHERE correlation_id=? AND result_code='SUCCESS'",
                String.class, actor.correlationId());
        assertTrue(detail.contains("stateMachineRevisionId"));
        assertTrue(detail.contains("executionContractId"));
    }

    @Test
    void createContractFailureRollsBackTaskPathContractTreeVersionAndReservation() {
        contractInsertFault.enabled = true;

        assertThrows(RuntimeException.class,
                () -> service.create(createCommand("T-ROLLBACK", keyPrefix + "-rollback", DIGEST_A), actor));

        contractInsertFault.enabled = false;
        assertEquals(0L, count("proj_project_task", "project_id=?", projectId));
        assertEquals(0L, count("proj_task_tree_path", "project_id=?", projectId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task_execution_contract contract "
                        + "JOIN proj_project_task task ON task.id=contract.project_task_id WHERE task.project_id=?",
                Long.class, projectId));
        assertEquals(0L, count("plt_idempotency_record", "idempotency_key=?", keyPrefix + "-rollback"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT task_tree_version FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? AND result_code='REJECTED'",
                Long.class, actor.correlationId()));
    }

    @Test
    void dependencyCasFailureRollsBackDependencyAndReservationAndWritesRejectionAudit() {
        long predecessorId = projectId + 11;
        long successorId = projectId + 12;
        insertTask(predecessorId, "T-PRE");
        insertTask(successorId, "T-SUCC");

        ServiceException failure = assertThrows(ServiceException.class, () -> service.addDependency(
                new AddDependencyCommand(successorId, 99, predecessorId, "FINISH_TO_START",
                        keyPrefix + "-dependency", DIGEST_A), actor));

        assertEquals(PROJECT_TASK_VERSION_CONFLICT.getCode(), failure.getCode());
        assertEquals(0L, count("proj_task_dependency", "project_id=?", projectId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project_task WHERE id=?", Integer.class, successorId));
        assertEquals(0L, count("plt_idempotency_record", "idempotency_key=?", keyPrefix + "-dependency"));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=? "
                        + "AND operation_code='PROJECT_TASK_DEPENDENCY_ADD' AND result_code='REJECTED'",
                Long.class, actor.correlationId()));
    }

    private CreateTaskCommand createCommand(String taskCode, String key, String digest) {
        return new CreateTaskCommand(projectId, taskCode, "集成任务", "S1", null, null,
                null, null, 2, 0, "Task 5 integration", key, digest);
    }

    private void insertProjectFixture() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0)",
                projectId, "F007-T5-" + projectId, projectId, 0,
                "F-PROJ-007 Task5 " + projectId, projectId, "/", 0, 0);
        jdbcTemplate.update("INSERT INTO proj_project_stage "
                        + "(id,project_id,stage_code,name,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,'S1','实施',1,'ACTIVE',0,0)", projectId + 1, projectId);
        jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                        + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,version,tenant_id) "
                        + "VALUES (?,?,1,'ACTIVE',?,1,1,0,0)", projectId + 2, projectId, keyPrefix + "-tree");
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(id,project_id,user_id,member_role,status,version,tenant_id) "
                        + "VALUES (?,?,9,'PROJECT_MANAGER','ACTIVE',0,0)", projectId + 3, projectId);
    }

    private void insertTask(long taskId, String taskCode) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,NULL,?,0,?,'S1',0,'IN_PROGRESS',0,0)",
                taskId, projectId, taskCode, taskCode, taskId, publishedRevisionId);
    }

    private long count(String table, String predicate, Object value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class, value);
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
            OperationAuditApiImpl.class, ProjectTaskCommandService.class, ProjectTaskProgressService.class,
            TaskExecutionContractFactory.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean ProjectTreeScopeService projectTreeScopeService() { return mock(ProjectTreeScopeService.class); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ContractInsertFault contractInsertFault() { return new ContractInsertFault(); }
        @Bean
        @Primary
        ProjectTaskExecutionContractMapper faultingContractMapper(
                @Qualifier("projectTaskExecutionContractMapper") ProjectTaskExecutionContractMapper delegate,
                ContractInsertFault fault) {
            return (ProjectTaskExecutionContractMapper) Proxy.newProxyInstance(
                    ProjectTaskExecutionContractMapper.class.getClassLoader(),
                    new Class<?>[]{ProjectTaskExecutionContractMapper.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (fault.enabled && "insert".equals(method.getName())) {
                                throw new IllegalStateException("PROJECT_TASK_CONTRACT_WRITE_FAILED_TEST");
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    static final class ContractInsertFault {
        volatile boolean enabled;
    }
}
