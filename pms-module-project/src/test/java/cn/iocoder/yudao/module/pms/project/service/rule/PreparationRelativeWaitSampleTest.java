package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRelativeTimeFacts;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The reusable draft is the input, not a separately maintained Java version of the sample. */
class PreparationRelativeWaitSampleTest {
    private static RuleEngineTestFixture engine;
    private static DmnEngine decisions;
    private static ProjectRuleSimulationService simulation;
    private static ProjectRulePublicationValidator publication;

    @BeforeAll static void start() {
        engine = new RuleEngineTestFixture();
        decisions = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        var compiler = new ProjectRuleCompiler();
        var tables = new ProjectDecisionTableService(decisions);
        simulation = new ProjectRuleSimulationService(compiler, engine.evaluator(), tables);
        publication = new ProjectRulePublicationValidator(compiler, tables, new TaskBusinessProviderRegistry(List.of(
                owner("SITE_SURVEY", "SURVEY_CONFIRMED"),
                owner("REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED"))));
    }

    @AfterAll static void stop() {
        if (decisions != null) decisions.close();
        if (engine != null) engine.close();
    }

    @Test void draftReopensCompilesAndFreezesWithoutCreatingAnotherRuleSource() throws IOException {
        var draft = sample();
        var reopened = TemplateRuleCollection.forEditing(JsonUtils.parseObject(JsonUtils.toJsonString(draft), TemplateDesignerDocument.class));
        assertEquals(draft.getRules(), reopened.getRules());
        assertNull(reopened.getTasks().getFirst().getCompletionRule());
        assertEquals(List.of("现场工勘", "需求分析"), reopened.getTasks().stream().map(TemplateDesignerDocument.TaskNode::getName).toList());
        assertEquals("工前准备", reopened.getStages().getFirst().getName());
        var issues = publication.validate(reopened);
        assertTrue(issues.isEmpty(), issues::toString);
        var compiled = new TemplateCompiler().compile(reopened);
        assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var frozen = JsonUtils.parseObject(JsonUtils.toJsonString(compiled.snapshot()), TemplateExecutionSnapshot.class);
        assertEquals(compiled.snapshot().getRulePrograms(), frozen.getRulePrograms());
        assertEquals("task:survey", frozen.getRulePrograms().get("analysis.admit").leaves().getFirst().parameters().path("sourceNodeKey").asText());
        assertNull(frozen.getStages().getFirst().getBinding());
        reopened.getTasks().getFirst().setNodeKey("task:renamed");
        assertTrue(publication.validate(reopened).stream().anyMatch(issue -> "RULE_TIME_SOURCE_UNAVAILABLE".equals(issue.code())));
        assertEquals("task:survey", frozen.getTasks().getFirst().getNodeKey());
    }

    @Test void admissionIsUnknownBeforeSurveyCompletionThenWaitsUntilTheExactDeadline() throws IOException {
        var draft = sample();
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, outcome(draft, "analysis.admit", Map.of("clock.now", value("2026-09-15T10:00:00+08:00"))));
        var source = value("2026-09-15T10:00:00+08:00");
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, outcome(draft, "analysis.admit", Map.of(
                "clock.completed:task:survey", source, "clock.now", value("2026-09-15T10:00:59+08:00"))));
        assertEquals(RuleEvaluation.Outcome.MATCHED, outcome(draft, "analysis.admit", Map.of(
                "clock.completed:task:survey", source, "clock.now", value("2026-09-15T10:01:00+08:00"))));
    }

    @Test void elapsedTimeDoesNotReplaceTheOriginalModulesCompletionResult() throws IOException {
        var draft = sample();
        var activation = value("2026-09-15T10:01:00+08:00");
        var now = value("2026-09-15T10:01:30+08:00");
        String fact = "BUSINESS_FACT:REQUIREMENT_ANALYSIS_COMPLETED:ALL";
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, outcome(draft, "analysis.complete", Map.of("clock.activation", activation, "clock.now", now)));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, outcome(draft, "analysis.complete", Map.of("clock.activation", activation,
                "clock.now", now, fact, JsonUtils.parseTree("false"))));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, outcome(draft, "analysis.complete", Map.of("clock.activation", activation,
                "clock.now", value("2026-09-15T10:01:29+08:00"), fact, JsonUtils.parseTree("true"))));
        assertEquals(RuleEvaluation.Outcome.MATCHED, outcome(draft, "analysis.complete", Map.of("clock.activation", activation,
                "clock.now", now, fact, JsonUtils.parseTree("true"))));
    }

    @Test void reworkUsesOnlyCurrentRoundTimesAndLeavesOldEvidenceUnchanged() throws IOException {
        var compiled = new TemplateCompiler().compile(sample());
        assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var params = compiled.snapshot().getRulePrograms().get("analysis.admit").leaves().getFirst().parameters();
        var old = new ProjectNodeExecutionDO();
        old.setId(41L); old.setNodeKey("task:survey"); old.setStatus("DONE");
        old.setEndedAt(LocalDateTime.of(2026, 9, 15, 10, 0));
        var previous = ProjectRelativeTimeFacts.boundary(params, 51L, List.of(old));
        assertNotNull(previous);
        var current = new ProjectNodeExecutionDO();
        current.setId(42L); current.setNodeKey("task:survey"); current.setStatus("ACTIVE");
        assertNull(ProjectRelativeTimeFacts.boundary(params, 51L, List.of(current)));
        current.setStatus("DONE"); current.setEndedAt(old.getEndedAt().plusHours(1));
        var renewed = ProjectRelativeTimeFacts.boundary(params, 51L, List.of(current));
        assertNotNull(renewed);
        assertEquals(42L, renewed.executionId());
        assertEquals(previous.dueAt().plusSeconds(3600), renewed.dueAt());
        assertEquals(LocalDateTime.of(2026, 9, 15, 10, 0), old.getEndedAt());

        var ownWait = compiled.snapshot().getRulePrograms().get("analysis.complete").leaves().stream()
                .filter(leaf -> "WAIT_ELAPSED".equals(leaf.predicate())).findFirst().orElseThrow().parameters();
        var analysis = new ProjectNodeExecutionDO(); analysis.setId(52L); analysis.setNodeKey("task:analysis");
        analysis.setAdmittedAt(current.getEndedAt().plusMinutes(1));
        analysis.setStartedAt(analysis.getAdmittedAt().plusSeconds(20));
        assertNull(ProjectRelativeTimeFacts.boundary(ownWait, 51L, List.of(analysis)));
        assertEquals(analysis.getAdmittedAt().atZone(ZoneId.systemDefault()).toInstant().plusSeconds(30),
                ProjectRelativeTimeFacts.boundary(ownWait, 52L, List.of(analysis)).dueAt());
    }

    private static TemplateDesignerDocument sample() throws IOException {
        try (var input = PreparationRelativeWaitSampleTest.class.getResourceAsStream("/project-template/preparation-relative-wait.json")) {
            assertNotNull(input);
            return JsonUtils.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8), TemplateDesignerDocument.class);
        }
    }

    private static RuleEvaluation.Outcome outcome(TemplateDesignerDocument draft, String key, Map<String, JsonNode> inputs) {
        return assertInstanceOf(RuleEvaluation.class, simulation.simulate(7L, draft.getRules(), key, inputs).evaluation()).outcome();
    }

    private static JsonNode value(String value) { return JsonUtils.parseTree(JsonUtils.toJsonString(value)); }

    private static TaskBusinessObjectProvider owner(String objectType, String fact) {
        var owner = mock(TaskBusinessObjectProvider.class);
        when(owner.ownerContext()).thenReturn("SOL");
        when(owner.objectType()).thenReturn(objectType);
        when(owner.completionFactCodes()).thenReturn(Set.of(fact));
        return owner;
    }
}
