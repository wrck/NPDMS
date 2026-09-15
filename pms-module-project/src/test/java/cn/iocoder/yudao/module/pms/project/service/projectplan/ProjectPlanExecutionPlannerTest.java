package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPlanExecutionPlannerTest {
    final ProjectPlanExecutionPlanner planner = new ProjectPlanExecutionPlanner();
    final TemplateExecutionSnapshot before = ProjectPlanImpactAnalyzerTest.snapshot();
    final List<ProjectNodeExecutionDO> rounds = new ArrayList<>();
    final List<ProjectStageInstanceDO> stages = new ArrayList<>();
    final List<ProjectTaskInstanceDO> tasks = new ArrayList<>();

    ProjectPlanExecutionPlannerTest() {
        long id = 10;
        for (var stage : before.getStages()) {
            var row = new ProjectStageInstanceDO(); row.setId(++id); row.setCode(stage.getCode()); row.setStatus("PENDING"); stages.add(row);
            rounds.add(round(stage.getNodeKey(), "STAGE", row.getId()));
        }
        var task = new ProjectTaskInstanceDO(); task.setId(20L); task.setCode("T1"); task.setStageCode("A"); task.setStatus("PENDING_ASSIGN"); tasks.add(task);
        rounds.add(round("task:one", "TASK", 20L));
    }
    @Test void mapsIdentityByNodeKeyNotCodeOrSourceAssetAndDoesNotInventRework() {
        var after = ProjectPlanImpactAnalyzerTest.copy(before);
        after.getTasks().getFirst().setCode("RENAMED");
        var added = new TemplateExecutionSnapshot.TaskContract(); added.setNodeKey("task:two"); added.setCode("T1"); added.setName("新任务"); after.getTasks().add(added);
        var result = plan(after);
        assertTrue(result.issues().isEmpty());
        var retained = change(result,"task:one");
        assertEquals(ProjectPlanExecutionPlanner.Action.REBASE_CURRENT, retained.action());
        assertEquals(20L, retained.nodeInstanceId()); assertEquals(120L, retained.executionId());
        assertEquals("T1",retained.fromCode()); assertEquals("RENAMED",retained.toCode()); assertEquals(1,retained.roundNo());
        var created = change(result,"task:two");
        assertEquals(ProjectPlanExecutionPlanner.Action.CREATE, created.action()); assertNull(created.nodeInstanceId()); assertNull(created.executionId());
    }
    @Test void completedHistoryStaysOnOriginalPlanWhileActiveWorkKeepsItsExecutionAndEvidence() {
        var completed = rounds.getFirst(); completed.setStatus("DONE"); completed.setPlanVersionId(40L);
        completed.setEndedAt(LocalDateTime.now()); completed.setResultSnapshot("{\"outcome\":\"MATCHED\"}"); stages.getFirst().setStatus("DONE");
        var active = rounds.getLast(); active.setStatus("ACTIVE"); active.setStartedAt(LocalDateTime.now()); active.setSubmittedAt(LocalDateTime.now());
        tasks.getFirst().setStatus("IN_PROGRESS");
        String frozenBefore = JsonUtils.toJsonString(rounds);
        var result = plan(ProjectPlanImpactAnalyzerTest.copy(before));
        assertTrue(result.issues().isEmpty());
        assertEquals(ProjectPlanExecutionPlanner.Action.PRESERVE_HISTORY, change(result,"stage:a").action());
        assertEquals(40L, change(result,"stage:a").sourcePlanVersionId());
        assertEquals(ProjectPlanExecutionPlanner.Action.REBASE_CURRENT, change(result,"task:one").action());
        assertEquals(120L,change(result,"task:one").executionId()); assertEquals(frozenBefore,JsonUtils.toJsonString(rounds));
    }
    @Test void missingAndDuplicateCurrentExecutionsCannotBeSilentlyRecreated() {
        rounds.removeLast();
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("CURRENT_EXECUTION_MISSING")));
        rounds.add(rounds.getFirst());
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("DUPLICATE_CURRENT_EXECUTION")));
    }
    @Test void activeOldPlanOrMismatchedTaskProjectionBlocksActivation() {
        rounds.getLast().setPlanVersionId(40L);
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("CURRENT_EXECUTION_STALE")));
        rounds.getLast().setPlanVersionId(50L); tasks.getFirst().setStatus("DONE");
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("NODE_RUNTIME_PROJECTION_STALE")));
    }
    @Test void onlyUnstartedNodesCanBeRetiredAndTheirRoundIsNotOverwritten() {
        var after = ProjectPlanImpactAnalyzerTest.copy(before); after.setTasks(List.of());
        assertEquals(ProjectPlanExecutionPlanner.Action.RETIRE_UNSTARTED,change(plan(after),"task:one").action());
        assertEquals("PENDING",rounds.getLast().getStatus()); assertEquals(1,rounds.getLast().getCurrentMarker());
        rounds.getLast().setStatus("ACTIVE"); tasks.getFirst().setStatus("IN_PROGRESS");
        assertTrue(plan(after).issues().stream().anyMatch(issue -> issue.code().equals("STARTED_NODE_DELETE_FORBIDDEN")));
    }
    @Test void completedExecutionWithoutEvidenceCannotBeRepairedByChangingThePlan() {
        rounds.getLast().setStatus("DONE"); tasks.getFirst().setStatus("DONE");
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("ENDED_EXECUTION_EVIDENCE_MISSING")));
    }
    @Test void admittedButUnstartedTasksRemainEditableAndCanBeRetiredWithoutRewritingStartedWork() {
        var round = rounds.getLast(); round.setStatus("ACTIVE"); round.setAdmittedAt(LocalDateTime.now());
        for (String status : List.of("PENDING_ASSIGN", "PENDING_START")) {
            tasks.getFirst().setStatus(status);
            assertTrue(plan(before).issues().isEmpty());
            assertEquals(ProjectPlanExecutionPlanner.Action.REBASE_CURRENT, change(plan(before), "task:one").action());
            var removed = ProjectPlanImpactAnalyzerTest.copy(before); removed.setTasks(List.of());
            assertEquals(ProjectPlanExecutionPlanner.Action.RETIRE_UNSTARTED, change(plan(removed), "task:one").action());
        }
        round.setStartedAt(LocalDateTime.now());
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("NODE_RUNTIME_PROJECTION_STALE")));
        tasks.getFirst().setStatus("IN_PROGRESS");
        var removed = ProjectPlanImpactAnalyzerTest.copy(before); removed.setTasks(List.of());
        assertTrue(plan(removed).issues().stream().anyMatch(issue -> issue.code().equals("STARTED_NODE_DELETE_FORBIDDEN")));
    }
    @Test void includesTheNodeEditVersionSoConcurrentMetadataChangesCannotHideBehindAnUnchangedRound() {
        tasks.getFirst().setVersion(6);
        var first = change(plan(before),"task:one");
        tasks.getFirst().setVersion(7);
        var second = change(plan(before),"task:one");
        assertEquals(first.executionVersion(),second.executionVersion());
        assertEquals(6,first.nodeVersion()); assertEquals(7,second.nodeVersion());
    }
    @Test void independentRuntimeTasksCannotLoseTheirTreeWhenAPlanIsInstalled() {
        var outside = new ProjectTaskInstanceDO(); outside.setId(21L); outside.setCode("MANUAL"); outside.setStageCode("A"); outside.setStatus("IN_PROGRESS");
        tasks.add(outside);
        assertTrue(plan(before).issues().stream().anyMatch(issue -> issue.code().equals("PROJECT_PLAN_UNTRACKED_TASK")));
    }
    ProjectPlanExecutionPlanner.Plan plan(TemplateExecutionSnapshot after) { return planner.plan(50L,before,after,rounds,stages,tasks); }
    ProjectPlanExecutionPlanner.Change change(ProjectPlanExecutionPlanner.Plan plan, String key) {
        return plan.changes().stream().filter(change -> key.equals(change.nodeKey())).findFirst().orElseThrow();
    }
    ProjectNodeExecutionDO round(String key, String kind, Long instanceId) {
        var round = new ProjectNodeExecutionDO(); round.setId(instanceId+100); round.setNodeInstanceId(instanceId); round.setContractId(instanceId+200);
        round.setNodeKey(key); round.setNodeKind(kind); round.setStatus("PENDING"); round.setCurrentMarker(1); round.setPlanVersionId(50L); round.setVersion(0); round.setRoundNo(1);
        return round;
    }
}
