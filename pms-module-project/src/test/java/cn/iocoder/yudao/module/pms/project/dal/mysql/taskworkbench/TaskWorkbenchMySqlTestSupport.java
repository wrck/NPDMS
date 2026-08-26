package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class TaskWorkbenchMySqlTestSupport {

    @Resource
    protected JdbcTemplate jdbcTemplate;
    @Resource
    protected TransactionTemplate transactionTemplate;

    protected long projectId;
    protected long publishedRevisionId;
    protected final List<Long> taskIds = new ArrayList<>();
    protected final List<Long> createdProjectIds = new ArrayList<>();
    protected final List<Long> createdRevisionIds = new ArrayList<>();

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

    protected void createFixture(int taskCount) {
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 975_000_000_000L + seed * 100L;
        publishedRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        insertProject(projectId);
        for (int index = 1; index <= taskCount; index++) {
            long taskId = projectId + index;
            insertTask(projectId, taskId, "T-" + index);
            taskIds.add(taskId);
        }
    }

    protected void insertProject(long id) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0)",
                id, "F007-T3-" + id, id, 0, "F-PROJ-007 Task3 " + id, id, "/", 0, 0);
        createdProjectIds.add(id);
    }

    protected void insertTask(long ownerProjectId, long taskId, String code) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,NULL,?,0,?,'S1',0,'PENDING_ASSIGN',0,0)",
                taskId, ownerProjectId, code, code, taskId, publishedRevisionId);
    }

    @AfterEach
    void cleanupFixture() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                for (Long id : createdProjectIds) {
                    statement.executeUpdate("DELETE FROM proj_project_task_completion_evaluation WHERE tenant_id=0 "
                            + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + id + ")");
                    statement.executeUpdate("DELETE FROM proj_project_task_assignment WHERE tenant_id=0 "
                            + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + id + ")");
                    statement.executeUpdate("DELETE FROM proj_task_dependency WHERE tenant_id=0 AND project_id=" + id);
                    statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE tenant_id=0 "
                            + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + id + ")");
                    statement.executeUpdate("DELETE FROM proj_task_tree_path WHERE tenant_id=0 AND project_id=" + id);
                    statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + id);
                    statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + id);
                }
                for (Long id : createdRevisionIds) {
                    statement.executeUpdate("DELETE FROM proj_task_state_transition WHERE tenant_id=0 AND revision_id=" + id);
                    statement.executeUpdate("DELETE FROM proj_task_state_machine_revision WHERE tenant_id=0 AND id=" + id);
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
        taskIds.clear();
        createdProjectIds.clear();
        createdRevisionIds.clear();
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }
}
