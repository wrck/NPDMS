package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectReworkPlannerTest {
    private final ProjectReworkPlanner planner = new ProjectReworkPlanner(new ProjectPlanImpactAnalyzer());

    @Test void reopensOnlySelectedTaskAndNecessaryEndedStageAndPreservesOtherCompletedResults() {
        var snapshot = snapshot();
        var stage = round("s", "STAGE", 1L, "DONE"); var first = round("t1","TASK",2L,"DONE"); var other = round("t2","TASK",3L,"DONE");
        var plan = planner.plan(snapshot, List.of(stage,first,other), List.of("t1"));
        assertTrue(plan.applicable());
        assertEquals(Set.of("s","t1"), new HashSet<>(plan.targets().stream().map(target -> target.node().nodeKey()).toList()));
        assertFalse(plan.targets().stream().filter(target -> target.node().nodeKey().equals("s")).findFirst().orElseThrow().selected());
        assertTrue(plan.affectedNodeKeys().contains("t2"));
        assertEquals("DONE", other.getStatus()); assertEquals(1, other.getRoundNo());
    }

    @Test void activeParentIsRetainedAndDependenciesAreNotAutomaticallyReworked() {
        var plan = planner.plan(snapshot(),List.of(round("s","STAGE",1L,"ACTIVE"),round("t1","TASK",2L,"DONE"),round("t2","TASK",3L,"DONE")),List.of("t1"));
        assertTrue(plan.applicable()); assertEquals(1,plan.targets().size());
    }

    @Test void gateDependentWorkIsPreviewedButOnlyTheChosenSourceGetsANewRound() {
        var snapshot = ProjectPlanImpactAnalyzerTest.gateSnapshot();
        var analysis = round("task:analysis", "TASK", 12L, "DONE"); analysis.setResultSnapshot("preserved-analysis");
        var plan = planner.plan(snapshot, List.of(round("stage:a", "STAGE", 1L, "ACTIVE"),
                round("stage:b", "STAGE", 2L, "DONE"), round("task:one", "TASK", 11L, "DONE"), analysis), List.of("task:one"));
        assertTrue(plan.applicable());
        assertEquals(List.of("task:one"), plan.targets().stream().map(target -> target.node().nodeKey()).toList());
        assertEquals(Set.of("gate:survey", "task:analysis", "stage:b", "$project"), plan.affectedNodeKeys());
        assertEquals("DONE", analysis.getStatus()); assertEquals(1, analysis.getRoundNo());
        assertEquals("preserved-analysis", analysis.getResultSnapshot());
    }

    @Test void unstartedOrRunningSelectionsAreNotSilentlyTerminated() {
        for (String status : List.of("PENDING","ACTIVE")) {
            var plan = planner.plan(snapshot(),List.of(round("s","STAGE",1L,"ACTIVE"),round("t1","TASK",2L,status)),List.of("t1"));
            assertFalse(plan.applicable()); assertEquals("REWORK_NODE_NOT_ENDED",plan.blockers().getFirst().code());
        }
    }

    @Test void stageReworkCannotAbandonUnselectedRunningTaskAndInvalidSelectionIsRejected() {
        var active = round("t2","TASK",3L,"ACTIVE"); active.setStartedAt(java.time.LocalDateTime.now());
        var rounds = List.of(round("s","STAGE",1L,"DONE"),round("t1","TASK",2L,"DONE"),active);
        assertFalse(planner.plan(snapshot(),rounds,List.of("s")).applicable());
        assertFalse(planner.plan(snapshot(),rounds,List.of("missing")).applicable());
        assertFalse(planner.plan(snapshot(),rounds,List.of("t1","t1")).applicable());
        assertFalse(planner.plan(snapshot(),rounds,List.of()).applicable());
    }

    static TemplateExecutionSnapshot snapshot() {
        var snapshot = new TemplateExecutionSnapshot();
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("s"); stage.setCode("PREP"); stage.setName("工前准备");
        snapshot.setStages(List.of(stage));
        var first = new TemplateExecutionSnapshot.TaskContract(); first.setNodeKey("t1"); first.setCode("SURVEY"); first.setName("现场工勘"); first.setStageCode("PREP");
        var other = new TemplateExecutionSnapshot.TaskContract(); other.setNodeKey("t2"); other.setCode("ANALYSIS"); other.setName("需求分析"); other.setStageCode("PREP");
        snapshot.setTasks(List.of(first,other)); return snapshot;
    }
    static ProjectNodeExecutionDO round(String key,String kind,Long id,String status) {
        var row = new ProjectNodeExecutionDO(); row.setId(id); row.setNodeInstanceId(id); row.setNodeKey(key); row.setNodeKind(kind);
        row.setStatus(status); row.setVersion(1); row.setRoundNo(1); row.setCurrentMarker(1); return row;
    }
}
