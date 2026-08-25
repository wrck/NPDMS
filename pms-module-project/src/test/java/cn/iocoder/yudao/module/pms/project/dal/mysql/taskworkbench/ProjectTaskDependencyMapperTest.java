package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskDependencyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskDependencyPathQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = TaskWorkbenchMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskDependencyMapperTest extends TaskWorkbenchMySqlTestSupport {

    @Resource
    private ProjectTaskDependencyMapper mapper;

    @BeforeEach
    void setUp() {
        createFixture(3);
    }

    @Test
    void shouldFindTransitivePathAndExposeCycleGuardFact() {
        insertDependency(taskIds.get(0), taskIds.get(1), "FINISH_TO_START");
        insertDependency(taskIds.get(1), taskIds.get(2), "START_TO_START");

        assertTrue(mapper.existsDependencyPath(query(taskIds.get(0), taskIds.get(2))));
        assertFalse(mapper.existsDependencyPath(query(taskIds.get(2), taskIds.get(0))));
        assertTrue(mapper.existsDependencyPath(query(taskIds.get(0), taskIds.get(2))));
        assertFalse(mapper.existsDependencyPath(new TaskDependencyPathQuery(
                1L, projectId, taskIds.get(0), taskIds.get(2))));
    }

    @Test
    void shouldRejectSelfUnknownTypeAndCrossProjectDependency() {
        assertThrows(DataAccessException.class,
                () -> insertDependency(taskIds.get(0), taskIds.get(0), "FINISH_TO_START"));
        assertThrows(DataAccessException.class,
                () -> insertDependency(taskIds.get(0), taskIds.get(1), "UNKNOWN"));

        long otherProjectId = projectId + 50;
        long otherTaskId = projectId + 51;
        insertProject(otherProjectId);
        insertTask(otherProjectId, otherTaskId, "OTHER-TASK");
        assertThrows(DataAccessException.class,
                () -> insertDependency(taskIds.get(0), otherTaskId, "FINISH_TO_START"));
    }

    private TaskDependencyPathQuery query(long fromTaskId, long toTaskId) {
        return new TaskDependencyPathQuery(0L, projectId, fromTaskId, toTaskId);
    }

    private void insertDependency(long predecessorTaskId, long successorTaskId, String type) {
        ProjectTaskDependencyDO dependency = new ProjectTaskDependencyDO();
        dependency.setTenantId(0L);
        dependency.setProjectId(projectId);
        dependency.setPredecessorTaskId(predecessorTaskId);
        dependency.setSuccessorTaskId(successorTaskId);
        dependency.setDependencyTypeCode(type);
        dependency.setVersion(0);
        mapper.insert(dependency);
    }
}
