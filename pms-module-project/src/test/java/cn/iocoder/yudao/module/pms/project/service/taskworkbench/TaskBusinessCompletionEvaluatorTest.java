package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkFact;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkedFacts;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PM-11: no native completion fallback; only locked explicit current-contract facts. */
class TaskBusinessCompletionEvaluatorTest {
    private final ProjectTaskBusinessService owner = mock(ProjectTaskBusinessService.class);
    private final TaskBusinessCompletionEvaluator evaluator = new TaskBusinessCompletionEvaluator(owner);
    private final String version = "a".repeat(64);

    @Test void emptyGroupNeverPassesEitherQuantifier() {
        for (String quantifier : List.of("ALL", "ANY")) {
            var result = evaluator.evaluate(contract(quantifier), version, List.of());
            assertFalse(result.satisfied());
            assertTrue(result.unmetItems().contains("BUSINESS_LINK_GROUP_EMPTY"));
        }
    }
    @Test void allAndAnyHaveDifferentResultsOverSameExplicitGroup() {
        var links = List.of(fact(1, true), fact(2, false));
        assertFalse(evaluator.evaluate(contract("ALL"), version, links).satisfied());
        assertTrue(evaluator.evaluate(contract("ANY"), version, links).satisfied());
    }
    @Test void unknownFactFailsClosedEvenWhenAnyOtherRecordPasses() {
        var unknown = new TaskBusinessLinkFact(2L, "object-2", "name", "rev2", Map.of(), List.of(), Set.of());
        var result = evaluator.evaluate(contract("ANY"), version, List.of(fact(1, true), unknown));
        assertFalse(result.satisfied());
        assertTrue(result.unmetItems().stream().anyMatch(code -> code.startsWith("BUSINESS_FACT_UNKNOWN")));
    }
    @Test void opaqueVersionsAndExactSourceIdentityAreFrozenWithoutMutatingSource() {
        var source = fact(1, true);
        var first = evaluator.evaluate(contract("ALL"), version, List.of(source));
        var second = evaluator.evaluate(contract("ANY"), version, List.of(source));
        assertTrue(first.satisfied()); assertTrue(second.satisfied());
        assertEquals(List.of(Map.of("linkId", 1L, "objectId", "object-1", "factVersion", "revision:1")),
                first.evidence().get("links"));
        assertEquals("revision:1", source.factVersion());
        assertEquals(Map.of("CONFIRMED", true), source.completionFacts());
    }
    @Test void unsupportedPredicateCannotBeHiddenBehindPassingAnyBranch() {
        var contract = contract("ANY");
        contract.setCompletionRuleSnapshot("""
                {"operator":"ANY","rules":[{"predicate":"BUSINESS_FACT","parameters":{"factCode":"CONFIRMED","quantifier":"ANY"}},
                {"predicate":"TASK_NATIVE_STATUS","parameters":{"requiredStatus":"DONE"}}]}
                """);
        assertFalse(evaluator.evaluate(contract, version, List.of(fact(1, true))).satisfied());
    }
    @Test void artifactPresenceOrGenericCompletedFactNeverMeansArchived() {
        var contract = contract("ALL");
        contract.setCompletionRuleSnapshot("{\"factCode\":\"SURVEY_ARCHIVED\",\"quantifier\":\"ALL\"}");
        var artifact = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.BusinessArtifact(
                "html-report", 1, "owner:report:html", "Report", "v1");
        var source = new TaskBusinessLinkFact(1L, "object-1", "name", "revision:1",
                Map.of("COMPLETED", true, "SURVEY_CONFIRMED", true, "SURVEY_ARCHIVED", false), List.of(artifact), Set.of("QUERY"));
        assertFalse(evaluator.evaluate(contract, version, List.of(source)).satisfied());
        var absent = new TaskBusinessLinkFact(1L, "object-1", "name", "revision:1",
                Map.of("COMPLETED", true), List.of(artifact), Set.of("QUERY"));
        var result = evaluator.evaluate(contract, version, List.of(absent));
        assertFalse(result.satisfied());
        assertTrue(result.unmetItems().contains("BUSINESS_FACT_UNKNOWN:SURVEY_ARCHIVED:object-1"));
    }
    @Test void nestedEmptyRuleGroupsAndInvalidQuantifiersFailClosed() {
        for (String snapshot : List.of("{\"operator\":\"ALL\",\"rules\":[]}",
                "{\"operator\":\"ANY\",\"rules\":[]}", "{\"factCode\":\"CONFIRMED\",\"quantifier\":\"NONE\"}")) {
            var contract = contract("ALL"); contract.setCompletionRuleSnapshot(snapshot);
            assertFalse(evaluator.evaluate(contract, version, List.of(fact(1, true))).satisfied());
        }
    }
    @Test void duplicateObjectReferencesCannotSatisfyAnExplicitGroup() {
        assertFalse(evaluator.evaluate(contract("ALL"), version, List.of(fact(1, true), fact(1, true))).satisfied());
    }
    @Test void callsLockedServiceAndUsesReturnedOwnerVersions() {
        var command = command(version);
        when(owner.lockAndRevalidateLinkedFacts(10L, 0L, 9L, "corr", version))
                .thenReturn(new TaskBusinessLinkedFacts(version, List.of(fact(1, true))));
        var result = evaluator.evaluateLocked(command, contract("ALL"), actor());
        assertTrue(result.satisfied());
        verify(owner).lockAndRevalidateLinkedFacts(10L, 0L, 9L, "corr", version);
        verifyNoMoreInteractions(owner);
    }
    @Test void oldAggregateVersionNeverCompletes() {
        when(owner.lockAndRevalidateLinkedFacts(10L, 0L, 9L, "corr", version))
                .thenReturn(new TaskBusinessLinkedFacts("b".repeat(64), List.of(fact(1, true))));
        assertFalse(evaluator.evaluateLocked(command(version), contract("ALL"), actor()).satisfied());
    }
    @Test void ownerDenialPropagatesAndCannotBecomeSuccess() {
        when(owner.lockAndRevalidateLinkedFacts(10L, 0L, 9L, "corr", version))
                .thenThrow(new IllegalStateException("OWNER_FORBIDDEN"));
        assertThrows(IllegalStateException.class, () -> evaluator.evaluateLocked(command(version), contract("ALL"), actor()));
    }
    @Test void missingVersionOrContractMismatchNeverCallsOwner() {
        assertFalse(evaluator.evaluateLocked(command(null), contract("ALL"), actor()).satisfied());
        var changed = contract("ALL"); changed.setContractVersion(3);
        assertFalse(evaluator.evaluateLocked(command(version), changed, actor()).satisfied());
        verifyNoInteractions(owner);
    }
    @Test void requiresCallingCommandTransaction() throws Exception {
        var annotation = TaskBusinessCompletionEvaluator.class.getMethod("evaluateLocked", TaskActionCommand.class,
                ProjectTaskExecutionContractDO.class, TaskWorkbenchActor.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, annotation.propagation());
    }
    private TaskBusinessLinkFact fact(long id, boolean passed) {
        return new TaskBusinessLinkFact(id, "object-" + id, "name", "revision:" + id,
                Map.of("CONFIRMED", passed), List.of(), Set.of("QUERY"));
    }
    private ProjectTaskExecutionContractDO contract(String quantifier) {
        var row = new ProjectTaskExecutionContractDO(); row.setId(20L); row.setContractVersion(2);
        row.setTargetContextCode("ENG"); row.setTargetObjectType("SURVEY");
        row.setCompletionRuleTypeCode("BUSINESS_FACT");
        row.setCompletionRuleSnapshot("{\"factCode\":\"CONFIRMED\",\"quantifier\":\"" + quantifier + "\"}");
        return row;
    }
    private TaskActionCommand command(String expected) {
        return new TaskActionCommand(10L, 3, "COMPLETE", null, 20L, 2, null, null,
                null, null, expected, "key", "c".repeat(64));
    }
    private TaskWorkbenchActor actor() { return new TaskWorkbenchActor(0L, 9L, "corr"); }
}
