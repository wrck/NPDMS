package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskCompletionEvaluationDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCloseUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskCompletionFactsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskLifecycleStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectTaskGovernanceGuardQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = TaskWorkbenchMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskAssignmentMapperTest extends TaskWorkbenchMySqlTestSupport {

    @Resource
    private ProjectTaskAssignmentMapper assignmentMapper;
    @Resource
    private ProjectTaskRuntimeMapper taskMapper;
    @Resource
    private ProjectTaskCompletionEvaluationMapper evaluationMapper;

    @BeforeEach
    void setUp() {
        createFixture(1);
    }

    @Test
    void shouldKeepOneCurrentAssignmentAndNeverReactivateHistory() {
        long taskId = taskIds.getFirst();
        LocalDateTime startedAt = LocalDateTime.now().minusHours(1);
        ProjectTaskAssignmentDO first = assignment(taskId, 1001L, startedAt, "首次指派");
        first.setId(taskId + 10);
        assertEquals(1, assignmentMapper.insertAssignment(first));

        ProjectTaskAssignmentDO locked = transactionTemplate.execute(status ->
                assignmentMapper.selectCurrentForUpdate(new TaskAssignmentLockQuery(0L, taskId)));
        assertEquals(first.getId(), locked.getId());
        assertThrows(DataIntegrityViolationException.class,
                () -> {
                    ProjectTaskAssignmentDO duplicate = assignment(taskId, 1002L, startedAt, "重复当前指派");
                    duplicate.setId(taskId + 11);
                    assignmentMapper.insertAssignment(duplicate);
                });

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime transferredAt = now.withNano(now.getNano() / 1_000_000 * 1_000_000);
        ProjectTaskAssignmentDO second = assignment(taskId, 1002L, transferredAt, "责任转派");
        second.setId(taskId + 12);
        transactionTemplate.executeWithoutResult(status -> {
            ProjectTaskAssignmentDO current = assignmentMapper.selectCurrentForUpdate(
                    new TaskAssignmentLockQuery(0L, taskId));
            assertEquals(1, assignmentMapper.closeCurrentIfMatch(new TaskAssignmentCloseUpdate(
                    0L, current.getId(), current.getVersion(), transferredAt, "fproj007-task3-test")));
            assertEquals(1, assignmentMapper.insertAssignment(second));
        });

        assertEquals(transferredAt, jdbcTemplate.queryForObject(
                "SELECT effective_to FROM proj_project_task_assignment WHERE id=?",
                LocalDateTime.class, first.getId()));
        assertEquals(second.getId(), transactionTemplate.execute(status ->
                assignmentMapper.selectCurrentForUpdate(new TaskAssignmentLockQuery(0L, taskId))).getId());
        assertEquals(0, assignmentMapper.closeCurrentIfMatch(new TaskAssignmentCloseUpdate(
                0L, first.getId(), 1, transferredAt.plusMinutes(1), "fproj007-task3-test")));
        assertNull(transactionTemplate.execute(status -> assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(1L, taskId))));
    }

    @Test
    void shouldAppendCompletionEvaluationOncePerIdempotencyKey() {
        long taskId = taskIds.getFirst();
        long contractId = taskId + 70;
        jdbcTemplate.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,tenant_id,project_task_id,work_binding_type_code,binding_parameter_snapshot,"
                        + "permission_policy_ref,completion_rule_type_code,completion_rule_snapshot,"
                        + "source_definition_version,contract_version,effective_from,creator,updater) "
                        + "VALUES (?,0,?,'TASK_NATIVE',JSON_OBJECT('schemaVersion',1),"
                        + "'PROJECT_TASK_NATIVE_DEFAULT','TASK_NATIVE_STATUS',"
                        + "JSON_OBJECT('schemaVersion',1,'requiredStatus','DONE'),1,1,NOW(3),'test','test')",
                contractId, taskId);

        ProjectTaskCompletionEvaluationDO first = evaluation(taskId, contractId, taskId + 80,
                "complete-once");
        assertEquals(1, evaluationMapper.insertEvaluation(first));
        ProjectTaskCompletionEvaluationDO duplicate = evaluation(taskId, contractId, taskId + 81,
                "complete-once");
        assertThrows(DataIntegrityViolationException.class,
                () -> evaluationMapper.insertEvaluation(duplicate));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task_completion_evaluation "
                        + "WHERE tenant_id=0 AND project_task_id=? AND idempotency_key='complete-once'",
                Long.class, taskId));
    }

    @Test
    void shouldLockAndAssignTaskWithVersionAndStatusCas() {
        long taskId = taskIds.getFirst();
        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(projectId, taskMapper.selectProjectForCommandForUpdate(
                    new ProjectTaskProjectLockQuery(0L, projectId)).getId());
            assertEquals(taskId, taskMapper.selectTaskForAssignmentForUpdate(
                    new TaskAssignmentCommandQuery(0L, projectId, taskId)).getId());
            assertEquals(1, taskMapper.assignTaskIfMatch(new TaskAssignmentStateUpdate(
                    0L, projectId, taskId, 0, "PENDING_ASSIGN", "PENDING_START", "fproj007-task6-test")));
            ProjectTaskAssignmentDO assignment = assignment(taskId, 1001L, LocalDateTime.now(), "首次指派");
            assignment.setId(taskId + 20);
            assertEquals(1, assignmentMapper.insertAssignment(assignment));
        });

        assertEquals("PENDING_START", jdbcTemplate.queryForObject(
                "SELECT status FROM proj_project_task WHERE id=?", String.class, taskId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT version FROM proj_project_task WHERE id=?", Integer.class, taskId));
        assertEquals(0, taskMapper.assignTaskIfMatch(new TaskAssignmentStateUpdate(
                0L, projectId, taskId, 0, "PENDING_ASSIGN", "PENDING_START", "fproj007-task6-test")));
        assertNull(transactionTemplate.execute(status -> taskMapper.selectTaskForAssignmentForUpdate(
                new TaskAssignmentCommandQuery(1L, projectId, taskId))));
    }

    @Test
    void shouldPersistLifecycleTimesProgressAndExposeCurrentGovernanceTruth() {
        long taskId = taskIds.getFirst();
        LocalDateTime startedAt = LocalDateTime.now().withNano(123_000_000);
        assertEquals(1, taskMapper.assignTaskIfMatch(new TaskAssignmentStateUpdate(
                0L, projectId, taskId, 0, "PENDING_ASSIGN", "PENDING_START", "fproj007-task7-test")));
        assertEquals(1, taskMapper.updateLifecycleIfMatch(new TaskLifecycleStateUpdate(
                0L, projectId, taskId, 1, "PENDING_START", "IN_PROGRESS",
                true, false, null, startedAt, "fproj007-task7-test")));
        assertEquals(1, taskMapper.updateLifecycleIfMatch(new TaskLifecycleStateUpdate(
                0L, projectId, taskId, 2, "IN_PROGRESS", "PENDING_ACCEPT",
                false, false, 99, startedAt.plusMinutes(1), "fproj007-task7-test")));
        LocalDateTime endedAt = startedAt.plusHours(1);
        assertEquals(1, taskMapper.updateLifecycleIfMatch(new TaskLifecycleStateUpdate(
                0L, projectId, taskId, 3, "PENDING_ACCEPT", "CLOSED",
                false, true, null, endedAt, "fproj007-task7-test")));

        assertEquals(startedAt, jdbcTemplate.queryForObject(
                "SELECT actual_start_time FROM proj_project_task WHERE id=?", LocalDateTime.class, taskId));
        assertEquals(endedAt, jdbcTemplate.queryForObject(
                "SELECT actual_end_time FROM proj_project_task WHERE id=?", LocalDateTime.class, taskId));
        assertEquals(99, jdbcTemplate.queryForObject(
                "SELECT progress FROM proj_project_task WHERE id=?", Integer.class, taskId));
        TaskCompletionFactsQuery facts = new TaskCompletionFactsQuery(0L, projectId, taskId);
        assertTrue(taskMapper.selectNonTerminalDescendantIdsForUpdate(facts).isEmpty());
        assertTrue(taskMapper.selectNonTerminalPredecessorIdsForUpdate(facts).isEmpty());
        assertEquals("CLOSED", taskMapper.selectListForGovernanceGuard(
                new ProjectTaskGovernanceGuardQuery(0L, Set.of(projectId))).getFirst().getStatus());
        assertEquals(0, taskMapper.selectListForGovernanceGuard(
                new ProjectTaskGovernanceGuardQuery(1L, Set.of(projectId))).size());
    }

    private static ProjectTaskAssignmentDO assignment(long taskId, long assigneeId,
                                                       LocalDateTime effectiveFrom, String reason) {
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setTenantId(0L);
        assignment.setProjectTaskId(taskId);
        assignment.setAssigneeUserId(assigneeId);
        assignment.setEffectiveFrom(effectiveFrom);
        assignment.setAssignedBy(9001L);
        assignment.setReason(reason);
        assignment.setVersion(0);
        return assignment;
    }

    private static ProjectTaskCompletionEvaluationDO evaluation(long taskId, long contractId,
                                                                  long id, String idempotencyKey) {
        ProjectTaskCompletionEvaluationDO evaluation = new ProjectTaskCompletionEvaluationDO();
        evaluation.setId(id);
        evaluation.setTenantId(0L);
        evaluation.setProjectTaskId(taskId);
        evaluation.setExecutionContractId(contractId);
        evaluation.setTaskVersion(0);
        evaluation.setContractVersion(1);
        evaluation.setEvaluationResultCode("PASS");
        evaluation.setCommandId("command-" + id);
        evaluation.setIdempotencyKey(idempotencyKey);
        evaluation.setEvaluatedBy(9001L);
        evaluation.setEvaluatedAt(LocalDateTime.now());
        evaluation.setVersion(0);
        return evaluation;
    }
}
