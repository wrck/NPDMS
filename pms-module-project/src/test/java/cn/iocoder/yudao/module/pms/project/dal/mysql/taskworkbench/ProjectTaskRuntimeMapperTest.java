package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.NewTaskTreePathInsert;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskBasicUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskMoveLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskStructureUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeVersionUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAncestorBatchQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVersionUpdate;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTaskRuntimeMapperTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskRuntimeMapperTest {

    private static final int DEPTH = 30;

    @Resource
    private ProjectTaskRuntimeMapper mapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;

    private long projectId;
    private long firstTaskId;
    private long secondRootTaskId;
    private long stateMachineRevisionId;

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
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 970_000_000_000L + seed * 100L;
        firstTaskId = projectId + 1;
        secondRootTaskId = projectId + 900;
        stateMachineRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        insertProject();
        insertChain();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.executeUpdate("DELETE FROM proj_project_task_assignment WHERE tenant_id=0 "
                        + "AND project_task_id IN (SELECT id FROM proj_project_task WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM proj_task_tree_path WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + projectId);
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
    }

    @Test
    void shouldQueryDepthThirtyTreeInFiveModesWithStableCursor() {
        Set<Long> visibleTasks = allTaskIds();

        List<ProjectTaskInstanceDO> children = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN, firstTaskId, null, null, null,
                null, null, 100, visibleTasks));
        assertEquals(List.of(firstTaskId + 1), ids(children));

        List<ProjectTaskInstanceDO> descendants = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS, null, firstTaskId, null, null,
                null, null, 100, visibleTasks));
        assertEquals(DEPTH - 1, descendants.size());
        assertEquals(firstTaskId + 1, descendants.getFirst().getId());
        assertEquals(firstTaskId + DEPTH - 1, descendants.getLast().getId());

        List<ProjectTaskInstanceDO> ancestors = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.ANCESTOR_CHAIN, null, firstTaskId + DEPTH - 1,
                null, null, null, null, 100, visibleTasks));
        assertEquals(DEPTH - 1, ancestors.size());
        assertTrue(ids(ancestors).contains(firstTaskId));

        List<ProjectTaskInstanceDO> businessLevel = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.BUSINESS_LEVEL, null, null, "LEVEL-EVEN", null,
                null, null, 100, visibleTasks));
        assertEquals(DEPTH / 2, businessLevel.size());

        List<ProjectTaskInstanceDO> located = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.LOCATE, null, null, null, "TASK-29",
                null, null, 100, visibleTasks));
        assertEquals(List.of(firstTaskId + 29), ids(located));

        List<ProjectTaskInstanceDO> firstPage = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS, null, firstTaskId, null, null,
                null, null, 5, visibleTasks));
        ProjectTaskInstanceDO cursor = firstPage.getLast();
        List<ProjectTaskInstanceDO> secondPage = mapper.selectTree(query(
                ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS, null, firstTaskId, null, null,
                cursor.getSortOrder(), cursor.getId(), 5, visibleTasks));
        assertEquals(firstTaskId + 6, secondPage.getFirst().getId());
        assertFalse(new HashSet<>(ids(firstPage)).removeAll(ids(secondPage)));
    }

    @Test
    void shouldReturnEmptyForEmptyScopeAndRejectCrossTenantFacts() {
        assertTrue(mapper.selectTree(query(ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN,
                null, null, null, null, null, null, 10, Set.of())).isEmpty());
        ProjectTaskTreeQuery crossTenant = ProjectTaskTreeQuery.builder()
                .tenantId(1L).projectIds(Set.of(projectId)).visibleTaskIds(allTaskIds())
                .mode(ProjectTaskTreeQuery.Mode.LOCATE).targetTaskId(firstTaskId).pageSize(10).build();
        assertTrue(mapper.selectTree(crossTenant).isEmpty());
    }

    @Test
    void shouldApplyStageBeforeTheStableCursorLimit() {
        for (int index = 0; index < 55; index++) {
            insertRootTaskWithStage(projectId + 2_000 + index, 1_000 + index, "S0");
        }
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            long taskId = projectId + 3_000 + index;
            insertRootTaskWithStage(taskId, 2_000 + index, "S2");
            expected.add(taskId);
        }

        List<ProjectTaskInstanceDO> page = mapper.selectTree(ProjectTaskTreeQuery.builder()
                .tenantId(0L).projectIds(Set.of(projectId))
                .visibilityQuery(new TaskVisibilityQuery(0L, projectId, 901L, true))
                .mode(ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN).stageCode("S2").pageSize(50).build());

        assertEquals(expected, ids(page));
    }

    @Test
    void shouldResolveAssigneeBodyAndAncestorPlaceholdersWithoutCrossTenantLeakage() {
        long assignedTaskId = firstTaskId + DEPTH - 1;
        jdbcTemplate.update("INSERT INTO proj_project_task_assignment "
                        + "(id,project_task_id,assignee_user_id,effective_from,effective_to,assigned_by,reason,"
                        + "version,creator,updater,tenant_id) VALUES (?,?,?,?,NULL,?,'test',0,'test','test',0)",
                projectId + 99, assignedTaskId, 900L, java.time.LocalDateTime.now(), 901L);

        TaskVisibilityQuery engineer = new TaskVisibilityQuery(0L, projectId, 900L, false);
        assertEquals(DEPTH, mapper.selectVisibleTaskIds(engineer).size());
        assertEquals(List.of(assignedTaskId), mapper.selectFullTaskIds(engineer));
        assertEquals(1L, mapper.selectStageCounts(engineer).getFirst().taskCount());

        TaskVisibilityQuery manager = new TaskVisibilityQuery(0L, projectId, 901L, true);
        assertEquals(DEPTH + 1, mapper.selectVisibleTaskIds(manager).size());
        assertEquals(DEPTH + 1, mapper.selectFullTaskIds(manager).size());
        assertEquals(DEPTH + 1L, mapper.selectStageCounts(manager).getFirst().taskCount());

        TaskVisibilityQuery crossTenant = new TaskVisibilityQuery(1L, projectId, 900L, true);
        assertTrue(mapper.selectVisibleTaskIds(crossTenant).isEmpty());
        assertTrue(mapper.selectFullTaskIds(crossTenant).isEmpty());
        assertTrue(mapper.selectStageCounts(crossTenant).isEmpty());
    }

    @Test
    void shouldBatchAllAncestorsBeyondPageLimitAndDeduplicateMultipleMatches() {
        int deepTreeDepth = 205;
        extendChainToDepth(deepTreeDepth);
        long penultimateTaskId = firstTaskId + deepTreeDepth - 2;
        long lastTaskId = firstTaskId + deepTreeDepth - 1;
        jdbcTemplate.update("INSERT INTO proj_project_task_assignment "
                        + "(id,project_task_id,assignee_user_id,effective_from,effective_to,assigned_by,reason,"
                        + "version,creator,updater,tenant_id) VALUES (?,?,?,?,NULL,?,'test',0,'test','test',0)",
                projectId + 998, lastTaskId, 900L, java.time.LocalDateTime.now(), 901L);

        List<ProjectTaskInstanceDO> ancestors = mapper.selectAncestors(new TaskAncestorBatchQuery(
                0L, projectId, 900L, false, Set.of(penultimateTaskId, lastTaskId)));

        assertEquals(deepTreeDepth - 1, ancestors.size());
        assertEquals(deepTreeDepth - 1, new HashSet<>(ids(ancestors)).size());
        assertTrue(ids(ancestors).contains(firstTaskId));
        assertTrue(ids(ancestors).contains(penultimateTaskId));
        assertFalse(ids(ancestors).contains(lastTaskId));
        assertTrue(mapper.selectAncestors(new TaskAncestorBatchQuery(
                0L, projectId, 902L, false, Set.of(lastTaskId))).isEmpty());
        assertTrue(mapper.selectAncestors(new TaskAncestorBatchQuery(
                1L, projectId, 901L, true, Set.of(lastTaskId))).isEmpty());
    }

    @Test
    void shouldLockProjectSourceTargetAndSubtreeAndDetectCycle() {
        ProjectTaskRuntimeMapper.ProjectTaskMoveLocks locks = transactionTemplate.execute(status ->
                mapper.selectMoveLocks(new ProjectTaskMoveLockQuery(
                        0L, projectId, firstTaskId + 1, firstTaskId + DEPTH - 1)));

        assertEquals(projectId, locks.project().getId());
        assertEquals(firstTaskId + 1, locks.sourceTask().getId());
        assertEquals(firstTaskId + DEPTH - 1, locks.targetParentTask().getId());
        assertEquals(DEPTH - 2, locks.movedSubtree().size());
        assertTrue(locks.targetInsideMovedSubtree());
    }

    @Test
    void shouldDetectCycleFromLockedCurrentFactsAfterRepeatableReadSnapshotBecomesStale() {
        long sourceTaskId = firstTaskId + 1;
        ProjectTaskRuntimeMapper.ProjectTaskMoveLocks locks;
        try (var executor = Executors.newSingleThreadExecutor()) {
            locks = transactionTemplate.execute(status -> {
                assertEquals(0L, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM proj_task_tree_path WHERE tenant_id=0 AND project_id=? "
                                + "AND ancestor_task_id=? AND descendant_task_id=?",
                        Long.class, projectId, sourceTaskId, secondRootTaskId));

                var concurrentMove = executor.submit(() -> transactionTemplate.executeWithoutResult(innerStatus -> {
                    ProjectTaskStructureUpdate update = new ProjectTaskStructureUpdate(
                            0L, projectId, secondRootTaskId, sourceTaskId, 0,
                            firstTaskId, 2, 2, "fproj007-task2-concurrent-test");
                    assertEquals(1, mapper.updateStructureIfMatch(update));
                    mapper.rebuildMovedSubtreePaths(update);
                    assertEquals(1, mapper.incrementTaskTreeVersion(
                            new ProjectTaskTreeVersionUpdate(0L, projectId, 0L,
                                    "fproj007-task2-concurrent-test")));
                }));
                try {
                    concurrentMove.get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("并发树移动未完成", exception);
                }
                return mapper.selectMoveLocks(new ProjectTaskMoveLockQuery(
                        0L, projectId, sourceTaskId, secondRootTaskId));
            });
        }

        assertTrue(locks.targetInsideMovedSubtree());
        assertTrue(ids(locks.movedSubtree()).contains(secondRootTaskId));
        assertEquals(sourceTaskId, jdbcTemplate.queryForObject(
                "SELECT parent_task_id FROM proj_project_task WHERE id=?", Long.class, secondRootTaskId));
        assertEquals(firstTaskId, jdbcTemplate.queryForObject(
                "SELECT parent_task_id FROM proj_project_task WHERE id=?", Long.class, sourceTaskId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE project_id=? "
                        + "AND ancestor_task_id=? AND descendant_task_id=?",
                Long.class, projectId, sourceTaskId, secondRootTaskId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT task_tree_version FROM proj_project WHERE id=?", Long.class, projectId));
    }

    @Test
    void shouldApplyClosureMoveAndKeepCasIndependent() {
        long movedTaskId = firstTaskId + 2;
        ProjectTaskStructureUpdate update = new ProjectTaskStructureUpdate(
                0L, projectId, movedTaskId, secondRootTaskId, 0,
                secondRootTaskId, 1, -1, "fproj007-task2-test");
        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(1, mapper.updateStructureIfMatch(update));
            mapper.rebuildMovedSubtreePaths(update);
            assertEquals(1, mapper.incrementTaskTreeVersion(
                    new ProjectTaskTreeVersionUpdate(0L, projectId, 0L, "fproj007-task2-test")));
        });

        assertEquals(secondRootTaskId, jdbcTemplate.queryForObject(
                "SELECT parent_task_id FROM proj_project_task WHERE id=?", Long.class, movedTaskId));
        assertEquals(secondRootTaskId, jdbcTemplate.queryForObject(
                "SELECT root_task_id FROM proj_project_task WHERE id=?", Long.class, movedTaskId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT tree_depth FROM proj_project_task WHERE id=?", Integer.class, movedTaskId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT task_tree_version FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT task_progress_version FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE project_id=? "
                        + "AND ancestor_task_id=? AND descendant_task_id=?",
                Long.class, projectId, secondRootTaskId, firstTaskId + DEPTH - 1));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE project_id=? "
                        + "AND ancestor_task_id=? AND descendant_task_id=?",
                Long.class, projectId, firstTaskId, firstTaskId + DEPTH - 1));

        assertEquals(0, mapper.updateStructureIfMatch(new ProjectTaskStructureUpdate(
                0L, projectId, movedTaskId, firstTaskId, 0,
                firstTaskId, 1, 0, "fproj007-task2-test")));
        assertEquals(0, mapper.incrementTaskTreeVersion(
                new ProjectTaskTreeVersionUpdate(0L, projectId, 0L, "fproj007-task2-test")));
    }

    @Test
    void shouldRollbackAdjacencyClosureAndWatermarkTogether() {
        long movedTaskId = firstTaskId + 2;
        ProjectTaskStructureUpdate update = new ProjectTaskStructureUpdate(
                0L, projectId, movedTaskId, secondRootTaskId, 0,
                secondRootTaskId, 1, -1, "fproj007-task2-rollback");

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            assertEquals(1, mapper.updateStructureIfMatch(update));
            mapper.rebuildMovedSubtreePaths(update);
            assertEquals(1, mapper.incrementTaskTreeVersion(
                    new ProjectTaskTreeVersionUpdate(0L, projectId, 0L, "fproj007-task2-rollback")));
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(firstTaskId + 1, jdbcTemplate.queryForObject(
                "SELECT parent_task_id FROM proj_project_task WHERE id=?", Long.class, movedTaskId));
        assertEquals(firstTaskId, jdbcTemplate.queryForObject(
                "SELECT root_task_id FROM proj_project_task WHERE id=?", Long.class, movedTaskId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT tree_depth FROM proj_project_task WHERE id=?", Integer.class, movedTaskId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT task_tree_version FROM proj_project WHERE id=?", Long.class, projectId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE project_id=? "
                        + "AND ancestor_task_id=? AND descendant_task_id=?",
                Long.class, projectId, firstTaskId, firstTaskId + DEPTH - 1));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE project_id=? "
                        + "AND ancestor_task_id=? AND descendant_task_id=?",
                Long.class, projectId, secondRootTaskId, firstTaskId + DEPTH - 1));
    }

    @Test
    void shouldPersistNewTaskPathsAndApplyTaskCasUpdates() {
        long newTaskId = projectId + 950;
        long parentTaskId = firstTaskId + 1;
        insertTask(newTaskId, parentTaskId, firstTaskId, 2, 950, "T-NEW", "L2");

        assertEquals(3, mapper.insertNewTaskPaths(new NewTaskTreePathInsert(
                0L, projectId, newTaskId, parentTaskId, "fproj007-task5-test")));
        assertEquals(3L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_task_tree_path WHERE tenant_id=0 AND project_id=? "
                        + "AND descendant_task_id=?",
                Long.class, projectId, newTaskId));

        assertEquals(1, mapper.updateBasicIfMatch(new ProjectTaskBasicUpdate(
                0L, newTaskId, 0, "更新任务", "L3", null, null,
                7, 951, "更新描述", "fproj007-task5-test",
                Set.of("name", "businessLevelCode", "priority", "sortOrder", "description"))));
        assertEquals("更新任务", jdbcTemplate.queryForObject(
                "SELECT name FROM proj_project_task WHERE id=?", String.class, newTaskId));
        assertEquals(1, mapper.incrementTaskVersionIfMatch(new TaskVersionUpdate(
                0L, newTaskId, 1, "fproj007-task5-test")));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project_task WHERE id=?", Integer.class, newTaskId));
        assertEquals(0, mapper.incrementTaskVersionIfMatch(new TaskVersionUpdate(
                0L, newTaskId, 1, "fproj007-task5-test")));
        assertEquals(1, mapper.updateBasicIfMatch(new ProjectTaskBasicUpdate(
                0L, newTaskId, 2, null, null, null, null,
                null, null, null, "fproj007-task5-test", Set.of("description"))));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT description FROM proj_project_task WHERE id=?", String.class, newTaskId));
    }

    private ProjectTaskTreeQuery query(ProjectTaskTreeQuery.Mode mode, Long parentTaskId,
                                       Long targetTaskId, String businessLevelCode, String keyword,
                                       Integer cursorSortOrder, Long cursorTaskId, int pageSize,
                                       Set<Long> visibleTaskIds) {
        return ProjectTaskTreeQuery.builder()
                .tenantId(0L).projectIds(Set.of(projectId)).visibleTaskIds(visibleTaskIds)
                .mode(mode).parentTaskId(parentTaskId).targetTaskId(targetTaskId)
                .businessLevelCode(businessLevelCode).keyword(keyword)
                .cursorSortOrder(cursorSortOrder).cursorTaskId(cursorTaskId).pageSize(pageSize)
                .build();
    }

    private Set<Long> allTaskIds() {
        Set<Long> ids = new HashSet<>();
        for (int index = 0; index < DEPTH; index++) ids.add(firstTaskId + index);
        ids.add(secondRootTaskId);
        return ids;
    }

    private static List<Long> ids(List<ProjectTaskInstanceDO> tasks) {
        return tasks.stream().map(ProjectTaskInstanceDO::getId).toList();
    }

    private void insertProject() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0)",
                projectId, "F007-T2-" + projectId, projectId, 0,
                "F-PROJ-007 Task2 " + projectId, projectId, "/", 0, 0);
    }

    private void insertChain() {
        for (int depth = 0; depth < DEPTH; depth++) {
            long taskId = firstTaskId + depth;
            Long parentId = depth == 0 ? null : taskId - 1;
            insertTask(taskId, parentId, firstTaskId, depth, depth, "TASK-" + depth,
                    depth % 2 == 0 ? "LEVEL-EVEN" : "LEVEL-ODD");
            for (int ancestorDepth = 0; ancestorDepth <= depth; ancestorDepth++) {
                long ancestorId = firstTaskId + ancestorDepth;
                insertPath(ancestorId, taskId, depth - ancestorDepth);
            }
        }
        insertTask(secondRootTaskId, null, secondRootTaskId, 0, 100,
                "TASK-SECOND-ROOT", "LEVEL-ROOT");
        insertPath(secondRootTaskId, secondRootTaskId, 0);
    }

    private void extendChainToDepth(int targetDepth) {
        List<Object[]> tasks = new ArrayList<>();
        for (int depth = DEPTH; depth < targetDepth; depth++) {
            long taskId = firstTaskId + depth;
            tasks.add(new Object[]{taskId, projectId, "TASK-" + depth, "TASK-" + depth,
                    taskId - 1, firstTaskId, depth,
                    depth % 2 == 0 ? "LEVEL-EVEN" : "LEVEL-ODD",
                    stateMachineRevisionId, depth});
        }
        jdbcTemplate.batchUpdate("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "business_level_code,state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S1',?,'PENDING_ASSIGN',0,0)", tasks);

        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                        + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                        + "SELECT ?, ancestor.id, descendant.id, "
                        + "descendant.tree_depth - ancestor.tree_depth, 0, 0 "
                        + "FROM proj_project_task ancestor "
                        + "JOIN proj_project_task descendant "
                        + "ON descendant.tenant_id=ancestor.tenant_id "
                        + "AND descendant.project_id=ancestor.project_id "
                        + "AND descendant.root_task_id=ancestor.root_task_id "
                        + "AND descendant.tree_depth>=ancestor.tree_depth "
                        + "WHERE ancestor.tenant_id=0 AND ancestor.project_id=? "
                        + "AND descendant.tree_depth>=? AND descendant.tree_depth<?",
                projectId, projectId, DEPTH, targetDepth);
    }

    private void insertTask(long id, Long parentId, long rootTaskId, int depth, int sortOrder,
                            String taskCode, String businessLevelCode) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "business_level_code,state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S1',?,'PENDING_ASSIGN',0,0)",
                id, projectId, taskCode, taskCode, parentId, rootTaskId, depth,
                businessLevelCode, stateMachineRevisionId, sortOrder);
    }

    private void insertRootTaskWithStage(long id, int sortOrder, String stageCode) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,root_task_id,tree_depth,state_machine_revision_id,"
                        + "stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,?,?,?,'PENDING_ASSIGN',0,0)",
                id, projectId, "STAGE-" + id, "STAGE-" + id, id,
                stateMachineRevisionId, stageCode, sortOrder);
        insertPath(id, id, 0);
    }

    private void insertPath(long ancestorTaskId, long descendantTaskId, int distance) {
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                        + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                        + "VALUES (?,?,?,?,0,0)",
                projectId, ancestorTaskId, descendantTaskId, distance);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
