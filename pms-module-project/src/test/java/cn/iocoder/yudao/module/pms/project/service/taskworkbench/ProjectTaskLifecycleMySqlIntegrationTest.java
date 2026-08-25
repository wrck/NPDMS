package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Resource TaskNativeBindingHostProvider nativeProvider;
    @Resource JdbcTemplate jdbcTemplate;

    private long projectId;
    private long taskId;
    private long contractId;
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
        when(nativeProvider.inspect(any())).thenReturn(new TaskBindingInspection(
                "TASK_NATIVE", Set.of("COMPLETE"), "0:1:1", null));
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
                    statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE project_task_id="
                            + taskId);
                    statement.executeUpdate("DELETE FROM proj_task_tree_path WHERE project_id=" + projectId);
                    statement.executeUpdate("DELETE FROM proj_project_task WHERE id=" + taskId);
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

    private void insertFixture() {
        Long revisionId = jdbcTemplate.queryForObject(
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
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformCommandExecutionApiImpl.class,
            OperationAuditApiImpl.class, ProjectTaskLifecycleService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TaskNativeBindingHostProvider nativeProvider() { return mock(TaskNativeBindingHostProvider.class); }
    }
}
