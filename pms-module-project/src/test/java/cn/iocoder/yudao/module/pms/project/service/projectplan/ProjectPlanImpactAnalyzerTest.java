package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPlanImpactAnalyzerTest {
    final ProjectPlanImpactAnalyzer analyzer = new ProjectPlanImpactAnalyzer();
    @Test void businessSourceDependencyIsIncludedInReworkAndPlanPreviewWithoutChangingHistory() {
        var before = snapshot();
        before.getRulePrograms().put("business-source", new ProjectRuleCompiler().compile(JsonUtils.parseTree("""
                {"predicate":"BUSINESS_FACT","parameters":{"sourceNodeKey":"task:one","factCode":"SURVEY_CONFIRMED","quantifier":"ANY"}}
                """)));
        before.getStages().get(1).setAdmissionRuleKey("business-source");
        before.setClosureRuleKey("business-source");
        assertTrue(analyzer.dependentNodeKeys(before, Set.of("task:one")).containsAll(Set.of("stage:b", "$project")));
        var after = copy(before); after.getTasks().getFirst().setName("修改工勘说明");
        var completed = round("stage:b", "STAGE", 2L); completed.setStatus("DONE");
        var result = analyzer.analyze(before, after, List.of(completed), List.of());
        assertTrue(result.changes().stream().anyMatch(change -> change.nodeKey().equals("stage:b") && change.completed()));
        assertEquals("DONE", completed.getStatus());
    }
    @Test void sharedRuleChangeIdentifiesAllConsumersButDoesNotReopenCompletedResults() {
        var before = snapshot(); var after = copy(before);
        after.getRulePrograms().put("done",new ProjectRuleCompiler().compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        var completed = round("stage:a","STAGE",1L); completed.setStatus("DONE");
        var result = analyzer.analyze(before,after,List.of(completed),List.of());
        assertEquals(Set.of("stage:a","stage:b","task:one"),new HashSet<>(result.changes().stream().map(ProjectPlanImpactAnalyzer.Change::nodeKey).toList()));
        assertEquals(List.of("done"),result.changedRuleKeys()); assertTrue(result.issues().isEmpty());
        assertTrue(result.changes().stream().filter(c -> c.nodeKey().equals("stage:a")).findFirst().orElseThrow().completed());
        assertEquals("DONE",completed.getStatus());
    }
    @Test void startedTaskBindingAndMembershipChangesAreRejected() {
        var before = snapshot(); var after = copy(before);
        after.getTasks().getFirst().getBinding().setType("BUSINESS_OBJECT"); after.getTasks().getFirst().setStageCode("B");
        var round = round("task:one","TASK",11L); round.setStartedAt(LocalDateTime.now());
        var result = analyzer.analyze(before,after,List.of(round),List.of());
        assertEquals(Set.of("STARTED_BINDING_IMMUTABLE","STARTED_STAGE_IMMUTABLE"),new HashSet<>(result.issues().stream().map(i -> i.code()).toList()));
    }
    @Test void startedTaskCannotReplaceItsSatisfactionTimingEvenWithoutStartTimestamp() {
        var before = snapshot(); var after = copy(before);
        after.getTasks().getFirst().setSatisfactionTiming("AFTER_INITIAL_ACCEPTANCE");
        var task = new ProjectTaskInstanceDO(); task.setId(11L); task.setStatus("IN_PROGRESS"); task.setStageCode("A");
        var result = analyzer.analyze(before,after,List.of(round("task:one","TASK",11L)),List.of(task));
        assertTrue(result.issues().stream().anyMatch(issue -> "STARTED_BINDING_IMMUTABLE".equals(issue.code())
                && issue.field().endsWith("satisfactionTiming")));
    }
    @Test void renamingTheSameStageDoesNotMoveItsStartedTask() {
        var before = snapshot(); var after = copy(before);
        after.getStages().getFirst().setCode("RENAMED"); after.getTasks().getFirst().setStageCode("RENAMED");
        var round = round("task:one","TASK",11L); round.setStartedAt(LocalDateTime.now());
        assertTrue(analyzer.analyze(before,after,List.of(round),List.of()).issues().isEmpty());
    }
    @Test void actualStartedWorkProtectsAnOtherwisePendingTaskAndItsStage() {
        var before = snapshot(); var after = copy(before); after.setStages(List.of(after.getStages().get(1))); after.setTasks(List.of());
        var task = new ProjectTaskInstanceDO(); task.setId(11L); task.setStageCode("A"); task.setStatus("PENDING_ASSIGN"); task.setActualStartTime(LocalDateTime.now());
        var result = analyzer.analyze(before,after,List.of(round("task:one","TASK",11L)),List.of(task));
        assertEquals(2,result.issues().stream().filter(i -> i.code().equals("STARTED_NODE_DELETE_FORBIDDEN")).count());
    }
    @Test void unstartedBranchesCanBeRemovedWithoutInventingACompletionRequirement() {
        var before = snapshot(); var after = copy(before); after.setStages(List.of(after.getStages().get(1))); after.setTasks(List.of());
        var result = analyzer.analyze(before,after,List.of(round("task:one","TASK",11L)),List.of());
        assertTrue(result.issues().isEmpty()); assertEquals(2,result.changes().size());
    }
    @Test void unchangedPlanProducesNoChangesAndClosureChangesAreExplicit() {
        var before = snapshot(); var after = copy(before);
        assertTrue(analyzer.analyze(before,after,List.of(),List.of()).changes().isEmpty());
        after.setClosureRuleKey("done");
        assertEquals("$project",analyzer.analyze(before,after,List.of(),List.of()).changes().getFirst().nodeKey());
    }
    @Test void followsCrossStageAndClosureDependenciesWithoutAutomaticallyReopeningHistory() {
        var before = snapshot();
        var compiler = new ProjectRuleCompiler();
        before.getStages().get(1).setAdmissionRuleKey("afterA");
        before.getRulePrograms().put("afterA",compiler.compile(JsonUtils.parseTree("{\"predicate\":\"STATE\",\"parameters\":{\"refCode\":\"A_COMPLETED\"}}")));
        before.setClosureRuleKey("afterB");
        before.getRulePrograms().put("afterB",compiler.compile(JsonUtils.parseTree("{\"predicate\":\"STATE\",\"parameters\":{\"refCode\":\"B_COMPLETED\"}}")));
        var after = copy(before); after.getStages().getFirst().setName("调整A");
        var completed = round("stage:b","STAGE",2L); completed.setStatus("DONE");
        var result = analyzer.analyze(before,after,List.of(completed),List.of());
        assertTrue(result.changes().stream().anyMatch(c -> c.nodeKey().equals("stage:b") && c.action().equals("REEVALUATE") && c.completed()));
        assertTrue(result.changes().stream().anyMatch(c -> c.nodeKey().equals("$project") && c.action().equals("REEVALUATE")));
        assertEquals("DONE",completed.getStatus());
    }
    static TemplateExecutionSnapshot snapshot() {
        var snapshot = new TemplateExecutionSnapshot();
        for (String code : List.of("A","B")) {
            var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("stage:"+code.toLowerCase()); stage.setCode(code); stage.setName(code); stage.setCompletionRuleKey("done"); snapshot.getStages().add(stage);
        }
        var task = new TemplateExecutionSnapshot.TaskContract(); task.setNodeKey("task:one"); task.setCode("T1"); task.setName("手工任务"); task.setStageCode("A"); task.setCompletionRuleKey("done");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("TASK_NATIVE"); task.setBinding(binding); snapshot.setTasks(List.of(task));
        snapshot.getRulePrograms().put("done",new ProjectRuleCompiler().compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        return snapshot;
    }
    static TemplateExecutionSnapshot copy(TemplateExecutionSnapshot snapshot) { return JsonUtils.parseObject(JsonUtils.toJsonString(snapshot),TemplateExecutionSnapshot.class); }
    static ProjectNodeExecutionDO round(String key, String kind, Long instanceId) {
        var round = new ProjectNodeExecutionDO(); round.setNodeKey(key); round.setNodeKind(kind); round.setNodeInstanceId(instanceId); round.setStatus("PENDING"); return round;
    }
}
