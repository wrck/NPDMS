package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskWorkbenchMySqlTestApplication;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskWorkbenchMySqlTestSupport;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = TaskWorkbenchMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskWorkbenchMySqlTest extends TaskWorkbenchMySqlTestSupport {

    @Resource
    private ProjectTaskRuntimeMapper taskMapper;

    @BeforeEach
    void setUpWorkbenchTree() {
        createFixture(3);
        long root = taskIds.get(0);
        long child = taskIds.get(1);
        long grandchild = taskIds.get(2);
        jdbcTemplate.update("UPDATE proj_project_task SET parent_task_id=?,root_task_id=?,tree_depth=1,"
                + "business_level_code='L1',sort_order=1 WHERE id=?", root, root, child);
        jdbcTemplate.update("UPDATE proj_project_task SET parent_task_id=?,root_task_id=?,tree_depth=2,"
                + "business_level_code='L2',sort_order=2 WHERE id=?", child, root, grandchild);
        jdbcTemplate.batchUpdate("INSERT INTO proj_task_tree_path "
                        + "(project_id,ancestor_task_id,descendant_task_id,distance,version,tenant_id) "
                        + "VALUES (?,?,?,?,0,0)", List.of(
                new Object[]{projectId, root, root, 0},
                new Object[]{projectId, root, child, 1},
                new Object[]{projectId, root, grandchild, 2},
                new Object[]{projectId, child, child, 0},
                new Object[]{projectId, child, grandchild, 1},
                new Object[]{projectId, grandchild, grandchild, 0}));
    }

    @Test
    void readsTheSameArbitraryDepthTreeThroughAllFiveSupportedProjections() {
        long root = taskIds.get(0);
        long child = taskIds.get(1);
        long grandchild = taskIds.get(2);

        assertEquals(List.of(child), ids(query(ProjectTaskTreeQuery.Mode.DIRECT_CHILDREN,
                root, null, null, null)));
        assertEquals(List.of(child, grandchild), ids(query(ProjectTaskTreeQuery.Mode.ALL_DESCENDANTS,
                null, root, null, null)));
        assertEquals(List.of(root, child), ids(query(ProjectTaskTreeQuery.Mode.ANCESTOR_CHAIN,
                null, grandchild, null, null)));
        assertEquals(List.of(grandchild), ids(query(ProjectTaskTreeQuery.Mode.BUSINESS_LEVEL,
                null, null, "L2", null)));
        assertEquals(List.of(grandchild), ids(query(ProjectTaskTreeQuery.Mode.LOCATE,
                null, grandchild, null, null)));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pms_project_task WHERE project_id=?", Long.class, projectId));
    }

    private List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> query(
            ProjectTaskTreeQuery.Mode mode, Long parentTaskId, Long targetTaskId,
            String businessLevelCode, String keyword) {
        List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> result =
                taskMapper.selectTree(ProjectTaskTreeQuery.builder()
                        .tenantId(0L).projectIds(Set.of(projectId))
                        .visibilityQuery(new TaskVisibilityQuery(0L, projectId, 9L, true))
                        .mode(mode).parentTaskId(parentTaskId).targetTaskId(targetTaskId)
                        .businessLevelCode(businessLevelCode).keyword(keyword).pageSize(100).build());
        return result;
    }

    private static List<Long> ids(
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> tasks) {
        return tasks.stream().map(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO::getId).toList();
    }
}
