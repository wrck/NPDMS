package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskWorkbenchMySqlTestApplication;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskWorkbenchMySqlTestSupport;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "fproj007.performance", matches = "true")
@SpringBootTest(classes = TaskWorkbenchMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskTreePerformanceTest extends TaskWorkbenchMySqlTestSupport {

    private static final long TWO_MILLION_PROJECT_ID = 995_000_000_000L;
    private static final long TWO_MILLION_TASK_BASE = 995_100_000_000L;
    private static final long LARGE_TREE_PROJECT_ID = 998_000_000_000L;
    private static final long LARGE_TREE_ROOT_ID = 998_100_000_000L;
    private static final long DEEP_TREE_PROJECT_ID = 999_000_000_000L;
    private static final long DEEP_TREE_ROOT_ID = 999_100_000_000L;
    private static final int SAMPLE_COUNT = 20;

    @Resource
    private ProjectTaskRuntimeMapper taskMapper;

    @Test
    void keepsPermissionFilteredQueriesBelowTwoSecondsAtTheLockedScale() {
        publishedRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        insertProjectRow(TWO_MILLION_PROJECT_ID, "F007-PERF-2M");
        insertMillionTasks(TWO_MILLION_PROJECT_ID, TWO_MILLION_TASK_BASE, 0);
        insertMillionTasks(TWO_MILLION_PROJECT_ID, TWO_MILLION_TASK_BASE, 1_000_000);
        assertEquals(2_000_000L, countTasks(TWO_MILLION_PROJECT_ID));

        insertLargeTree();
        assertEquals(50_000L, countTasks(LARGE_TREE_PROJECT_ID));
        assertEquals(2_000L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task WHERE project_id=? AND parent_task_id=?",
                Long.class, LARGE_TREE_PROJECT_ID, LARGE_TREE_ROOT_ID));

        insertDeepTree(31);
        List<Long> ancestors = ids(query(DEEP_TREE_PROJECT_ID, ProjectTaskTreeQuery.Mode.ANCESTOR_CHAIN,
                null, DEEP_TREE_ROOT_ID + 31, 100));
        assertEquals(31, ancestors.size());

        assertP95("two-million roots", () -> query(TWO_MILLION_PROJECT_ID,
                ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN, null, null, 100));
        assertP95("two-thousand direct children", () -> query(LARGE_TREE_PROJECT_ID,
                ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN, LARGE_TREE_ROOT_ID, null, 2_000));
        assertP95("fifty-thousand descendants", () -> query(LARGE_TREE_PROJECT_ID,
                ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS, null, LARGE_TREE_ROOT_ID, 200));
    }

    private void insertMillionTasks(long ownerProjectId, long baseId, int offset) {
        String sequence = sixDigitSequence();
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,root_task_id,tree_depth,state_machine_revision_id,"
                        + "stage_code,sort_order,status,version,tenant_id) "
                        + "SELECT ?+seq.n,?,CONCAT('PERF-',?+seq.n),CONCAT('PERF-',?+seq.n),"
                        + "?+seq.n,0,?,'S1',?+seq.n,'PENDING_ASSIGN',0,0 "
                        + "FROM (" + sequence + ") seq",
                baseId + offset, ownerProjectId, offset, offset, baseId + offset,
                publishedRevisionId, offset);
    }

    private String sixDigitSequence() {
        String digits = "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 "
                + "UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 "
                + "UNION ALL SELECT 8 UNION ALL SELECT 9)";
        return "SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS n "
                + "FROM " + digits + " d0 CROSS JOIN " + digits + " d1 CROSS JOIN " + digits
                + " d2 CROSS JOIN " + digits + " d3 CROSS JOIN " + digits + " d4 CROSS JOIN " + digits + " d5";
    }

    private void insertLargeTree() {
        insertProjectRow(LARGE_TREE_PROJECT_ID, "F007-PERF-50K");
        insertTaskRow(LARGE_TREE_PROJECT_ID, LARGE_TREE_ROOT_ID, null, LARGE_TREE_ROOT_ID, 0, 0);
        for (int from = 1; from <= 2_000; from += 500) {
            insertLargeTreeRange(from, Math.min(from + 499, 2_000), 1);
        }
        for (int from = 2_001; from < 50_000; from += 500) {
            insertLargeTreeRange(from, Math.min(from + 499, 49_999), 2);
        }
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                + "SELECT project_id,id,id,0,0,0 FROM proj_project_task WHERE project_id=?", LARGE_TREE_PROJECT_ID);
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                + "SELECT project_id,?,id,tree_depth,0,0 FROM proj_project_task "
                + "WHERE project_id=? AND id<>?", LARGE_TREE_ROOT_ID, LARGE_TREE_PROJECT_ID, LARGE_TREE_ROOT_ID);
        jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                + "SELECT project_id,parent_task_id,id,1,0,0 FROM proj_project_task "
                + "WHERE project_id=? AND tree_depth=2", LARGE_TREE_PROJECT_ID);
    }

    private void insertLargeTreeRange(int from, int to, int depth) {
        List<Object[]> rows = new ArrayList<>(to - from + 1);
        for (int index = from; index <= to; index++) {
            long taskId = LARGE_TREE_ROOT_ID + index;
            long parentId = depth == 1 ? LARGE_TREE_ROOT_ID
                    : LARGE_TREE_ROOT_ID + 1 + ((index - 2_001) % 2_000);
            rows.add(new Object[]{taskId, LARGE_TREE_PROJECT_ID, "TREE-" + index, "TREE-" + index,
                    parentId, LARGE_TREE_ROOT_ID, depth, publishedRevisionId, index});
        }
        jdbcTemplate.batchUpdate("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,'S1',?,'PENDING_ASSIGN',0,0)", rows);
    }

    private void insertDeepTree(int depth) {
        insertProjectRow(DEEP_TREE_PROJECT_ID, "F007-PERF-DEEP");
        for (int index = 0; index <= depth; index++) {
            long taskId = DEEP_TREE_ROOT_ID + index;
            insertTaskRow(DEEP_TREE_PROJECT_ID, taskId,
                    index == 0 ? null : taskId - 1, DEEP_TREE_ROOT_ID, index, index);
            for (int ancestor = 0; ancestor <= index; ancestor++) {
                jdbcTemplate.update("INSERT INTO proj_task_tree_path "
                                + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                                + "VALUES (?,?,?,?,0,0)",
                        DEEP_TREE_PROJECT_ID, DEEP_TREE_ROOT_ID + ancestor, taskId, index - ancestor);
            }
        }
    }

    private void insertProjectRow(long id, String code) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0)",
                id, code, id, 0, code, id, "/", 0, 0);
    }

    private void insertTaskRow(long ownerProjectId, long taskId, Long parentTaskId,
                               long rootTaskId, int depth, int sortOrder) {
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,parent_task_id,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,'S1',?,'PENDING_ASSIGN',0,0)",
                taskId, ownerProjectId, "DEEP-" + taskId, "DEEP-" + taskId,
                parentTaskId, rootTaskId, depth, publishedRevisionId, sortOrder);
    }

    private List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> query(
            long ownerProjectId, ProjectTaskTreeQuery.Mode mode, Long parentTaskId,
            Long targetTaskId, int pageSize) {
        return taskMapper.selectTree(ProjectTaskTreeQuery.builder()
                .tenantId(0L).projectIds(Set.of(ownerProjectId))
                .visibilityQuery(new TaskVisibilityQuery(0L, ownerProjectId, 9L, true))
                .mode(mode).parentTaskId(parentTaskId).targetTaskId(targetTaskId)
                .pageSize(pageSize).build());
    }

    private void assertP95(String label, Supplier<List<?>> query) {
        query.get();
        List<Long> samples = new ArrayList<>(SAMPLE_COUNT);
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            long started = System.nanoTime();
            assertTrue(!query.get().isEmpty(), label + " should return rows");
            samples.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(samples);
        long p95 = samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
        assertTrue(p95 <= 2_000, label + " P95=" + p95 + "ms exceeds 2000ms; samples=" + samples);
    }

    private long countTasks(long ownerProjectId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task WHERE project_id=?", Long.class, ownerProjectId);
    }

    private static List<Long> ids(
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> tasks) {
        return tasks.stream().map(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO::getId).toList();
    }
}
