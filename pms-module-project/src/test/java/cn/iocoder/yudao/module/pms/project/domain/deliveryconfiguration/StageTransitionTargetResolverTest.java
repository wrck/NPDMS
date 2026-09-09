package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.SATISFIED;
import static cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.UNSATISFIED;
import static cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.resolve;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PM-03 / F-PROJ-009 AC-02、AC-03：用真实图和调用者事实验证解析，不 Mock 谓词或 Owner 查询。 */
class StageTransitionTargetResolverTest {

    @Test
    void standardChainResolvesEveryActualNextStageAndThenTerminal() {
        List<StageTransitionGraph.Stage> stages = new ArrayList<>();
        List<StageTransitionDefinition> edges = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            stages.add(stage("S" + i, i == 0, i == 6));
            if (i < 6) {
                edges.add(new StageTransitionDefinition("T" + i, "S" + i, "S" + (i + 1), null, 1, false));
            }
        }
        StageTransitionGraph graph = new StageTransitionGraph(stages, edges);
        for (int i = 0; i < 6; i++) {
            assertResolved(resolve(graph, "S" + i, List.of()), "T" + i, "S" + (i + 1));
        }
        assertTerminal(resolve(graph, "S6", List.of()));
    }

    @Test
    void presalesJumpsStraightToS4AndDoesNotCreateS6OrClosureFacts() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("DIRECT", "S0", "S4", null, 1, false));
        assertResolved(resolve(graph, "S0", List.of()), "DIRECT", "S4");
        assertTerminal(resolve(graph, "S4", List.of()));
        assertEquals(List.of("S0", "S4"), graph.stages().stream()
                .map(StageTransitionGraph.Stage::stageCode).toList());
    }

    @Test
    void anExplicitS0OnlyTerminalIsNotExpanded() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, true)), List.of());
        assertTerminal(resolve(graph, "S0", List.of()));
    }

    @Test
    void eachOfMultipleClosuresReturnsTerminal() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 1, false),
                edge("B", "S2", null, 2, true));
        assertTerminal(resolve(graph, "S1", List.of()));
        assertTerminal(resolve(graph, "S2", List.of(fact(11, UNAVAILABLE))));
    }

    @Test
    void unconditionalNonDefaultIsAlwaysTrueAndBeatsDefaultEvenAtLargerNumber() {
        StageTransitionGraph graph = branches(edge("ALWAYS", "S1", null, Integer.MAX_VALUE, false),
                edge("DEFAULT", "S2", null, Integer.MIN_VALUE, true));
        assertResolved(resolve(graph, "S0", List.of()), "ALWAYS", "S1");
    }

    @Test
    void smallerPriorityWinsRegardlessOfCodeOrderOrIntegerExtremes() {
        StageTransitionGraph graph = branches(edge("A_LOW", "S1", 11L, Integer.MAX_VALUE, false),
                edge("Z_HIGH", "S2", 12L, Integer.MIN_VALUE, false));
        assertResolved(resolve(graph, "S0", List.of(fact(11, SATISFIED), fact(12, SATISFIED))), "Z_HIGH", "S2");
    }

    @Test
    void unsatisfiedHigherPriorityDoesNotHideSatisfiedLowerPriority() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 1, false), edge("B", "S2", 12L, 2, false));
        assertResolved(resolve(graph, "S0", List.of(fact(11, UNSATISFIED), fact(12, SATISFIED))), "B", "S2");
    }

    @Test
    void defaultIsUsedOnlyWhenEveryNonDefaultIsExplicitlyUnsatisfied() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 1, false),
                edge("B", "S2", 12L, 2, false), edge("FALLBACK", "S2", null, -1, true));
        assertResolved(resolve(graph, "S0", List.of(fact(11, UNSATISFIED), fact(12, UNSATISFIED))),
                "FALLBACK", "S2");
        assertResolved(resolve(graph, "S0", List.of(fact(11, UNSATISFIED), fact(12, SATISFIED))), "B", "S2");
    }

    @Test
    void aSoleDefaultNeedsNoConditionFact() {
        assertResolved(resolve(single(new StageTransitionDefinition("DEFAULT", "S0", "S4", null, 1, true)),
                "S0", List.of()), "DEFAULT", "S4");
    }

    @Test
    void sameHighestPriorityMatchesReturnAmbiguousAndNoTarget() {
        StageTransitionGraph graph = branches(edge("B", "S1", 11L, 1, false),
                edge("A", "S2", 12L, 1, false), edge("DEFAULT", "S2", null, 0, true));
        StageTransitionTargetResolver.Result result = resolve(graph, "S0",
                List.of(fact(11, SATISFIED), fact(12, SATISFIED)));
        assertRejected(result, StageTransitionTargetResolver.Status.AMBIGUOUS,
                "AMBIGUOUS_PRIORITY", "transitions[A].priority");
        assertEquals(List.of("A", "B"), result.candidateTransitionCodes());
    }

    @Test
    void competingRelationsAreAmbiguousEvenWhenTheyShareTheTarget() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", 11L, 1, false),
                new StageTransitionDefinition("B", "S0", "S4", 12L, 1, false));
        assertRejected(resolve(graph, "S0", List.of(fact(11, SATISFIED), fact(12, SATISFIED))),
                StageTransitionTargetResolver.Status.AMBIGUOUS, "AMBIGUOUS_PRIORITY", "transitions[A].priority");
    }

    @Test
    void conditionalAndUnconditionalTieIsDecidedByTheSuppliedFact() {
        StageTransitionGraph graph = branches(edge("ALWAYS", "S1", null, 1, false),
                edge("CONDITIONAL", "S2", 11L, 1, false));
        assertResolved(resolve(graph, "S0", List.of(fact(11, UNSATISFIED))), "ALWAYS", "S1");
        assertRejected(resolve(graph, "S0", List.of(fact(11, SATISFIED))),
                StageTransitionTargetResolver.Status.AMBIGUOUS, "AMBIGUOUS_PRIORITY", "transitions[ALWAYS].priority");
    }

    @Test
    void lowerPriorityTiesDoNotDisplaceOneUniqueHigherPriorityMatch() {
        StageTransitionGraph graph = branches(edge("WIN", "S1", 11L, 1, false),
                edge("LOW_A", "S2", 12L, 2, false), edge("LOW_B", "S2", 13L, 2, false));
        assertResolved(resolve(graph, "S0", List.of(fact(11, SATISFIED), fact(12, SATISFIED), fact(13, SATISFIED))),
                "WIN", "S1");
    }

    @Test
    void missingFactAndUnavailableFactBothRejectInsteadOfUsingDefault() {
        StageTransitionGraph graph = branches(edge("CHECK", "S1", 11L, 1, false),
                edge("DEFAULT", "S2", null, 2, true));
        for (List<StageTransitionTargetResolver.ConditionFact> facts : List.of(
                List.<StageTransitionTargetResolver.ConditionFact>of(), List.of(fact(11, UNAVAILABLE)))) {
            StageTransitionTargetResolver.Result result = resolve(graph, "S0", facts);
            assertRejected(result, StageTransitionTargetResolver.Status.UNAVAILABLE,
                    "CONDITION_UNAVAILABLE", "transitions[CHECK].conditionRevisionId");
            assertEquals(List.of("CHECK"), result.candidateTransitionCodes());
            assertTrue(result.failures().getFirst().message().contains("11"));
        }
    }

    @Test
    void oneUnsatisfiedFactCannotMaskAnotherMissingFact() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 1, false),
                edge("B", "S2", 12L, 2, false), edge("DEFAULT", "S2", null, 3, true));
        assertRejected(resolve(graph, "S0", List.of(fact(11, UNSATISFIED))),
                StageTransitionTargetResolver.Status.UNAVAILABLE, "CONDITION_UNAVAILABLE",
                "transitions[B].conditionRevisionId");
    }

    @Test
    void unknownParticipatingConditionRejectsEvenWithAnUnconditionalHigherPriorityMatch() {
        StageTransitionGraph graph = branches(edge("KNOWN", "S1", null, 1, false),
                edge("UNKNOWN", "S2", 11L, 100, false));
        assertRejected(resolve(graph, "S0", List.of(fact(11, UNAVAILABLE))),
                StageTransitionTargetResolver.Status.UNAVAILABLE, "CONDITION_UNAVAILABLE",
                "transitions[UNKNOWN].conditionRevisionId");
    }

    @Test
    void unknownTakesPrecedenceOverAnOtherwiseAmbiguousDecision() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 1, false),
                edge("B", "S2", 12L, 1, false), edge("UNKNOWN", "S2", 13L, 2, false));
        assertRejected(resolve(graph, "S0", List.of(fact(11, SATISFIED), fact(12, SATISFIED))),
                StageTransitionTargetResolver.Status.UNAVAILABLE, "CONDITION_UNAVAILABLE",
                "transitions[UNKNOWN].conditionRevisionId");
    }

    @Test
    void conditionsOnOtherStagesDoNotParticipateInCurrentDecision() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, false),
                stage("S1", false, false), stage("S4", false, true)), List.of(
                new StageTransitionDefinition("FIRST", "S0", "S1", 11L, 1, false),
                new StageTransitionDefinition("LATER", "S1", "S4", 12L, 1, false)));
        assertResolved(resolve(graph, "S0", List.of(fact(11, SATISFIED), fact(12, UNAVAILABLE))), "FIRST", "S1");
        assertRejected(resolve(graph, "S1", List.of(fact(11, SATISFIED))),
                StageTransitionTargetResolver.Status.UNAVAILABLE, "CONDITION_UNAVAILABLE",
                "transitions[LATER].conditionRevisionId");
    }

    @Test
    void noMatchWithoutDefaultIsExplicitAndDoesNotAdvance() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("CHECK", "S0", "S4", 11L, 1, false));
        StageTransitionTargetResolver.Result result = resolve(graph, "S0", List.of(fact(11, UNSATISFIED)));
        assertRejected(result, StageTransitionTargetResolver.Status.NO_MATCH, "NO_MATCH", "stages[S0].outgoing");
        assertEquals(List.of("CHECK"), result.candidateTransitionCodes());
    }

    @Test
    void suppliedFactsMustMatchTheExactRevision() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("CHECK", "S0", "S4", 11L, 1, false));
        assertRejected(resolve(graph, "S0", List.of(fact(10, SATISFIED))),
                StageTransitionTargetResolver.Status.UNAVAILABLE, "CONDITION_UNAVAILABLE",
                "transitions[CHECK].conditionRevisionId");
    }

    @Test
    void oneExactRevisionFactMayBeSharedBySeveralRelations() {
        StageTransitionGraph graph = branches(edge("A", "S1", 11L, 2, false), edge("B", "S2", 11L, 1, false));
        assertResolved(resolve(graph, "S0", List.of(fact(11, SATISFIED))), "B", "S2");
    }

    @Test
    void invalidGraphIsRejectedBeforeAnyTargetOrTerminalIsReturned() {
        assertRejected(resolve(null, "S0", List.of()), StageTransitionTargetResolver.Status.INVALID_GRAPH,
                "REQUIRED", "graph");
        assertRejected(resolve(new StageTransitionGraph(List.of(), List.of()), "S0", List.of()),
                StageTransitionTargetResolver.Status.INVALID_GRAPH, "EMPTY_GRAPH", "stages");
        StageTransitionGraph invalid = branches(edge("A", "S1", null, 1, false), edge("B", "S2", null, 1, false));
        assertRejected(resolve(invalid, "S1", List.of()), StageTransitionTargetResolver.Status.INVALID_GRAPH,
                "UNCONDITIONAL_PRIORITY_CONFLICT", "transitions[B].priority");
    }

    @Test
    void nullOrUnknownCurrentStageIsLocated() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", null, 1, false));
        for (String current : Arrays.asList(null, "", "S1", "S7")) {
            assertRejected(resolve(graph, current, List.of()), StageTransitionTargetResolver.Status.INVALID_INPUT,
                    "UNKNOWN_CURRENT_STAGE", "currentStageCode");
        }
    }

    @Test
    void nullFactListFactEntryAndStatusAreExplicitInputFailures() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", 11L, 1, false));
        assertRejected(resolve(graph, "S0", null), StageTransitionTargetResolver.Status.INVALID_INPUT,
                "REQUIRED", "facts");
        assertRejected(resolve(graph, "S0", Arrays.asList((StageTransitionTargetResolver.ConditionFact) null)),
                StageTransitionTargetResolver.Status.INVALID_INPUT, "REQUIRED", "facts[0]");
        assertRejected(resolve(graph, "S0", List.of(fact(11, null))),
                StageTransitionTargetResolver.Status.INVALID_INPUT, "REQUIRED", "facts[0].status");
    }

    @Test
    void nonPositiveAndNullFactRevisionIdsAreRejected() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", 11L, 1, false));
        for (Long revision : Arrays.asList(null, 0L, -1L)) {
            assertRejected(resolve(graph, "S0", List.of(new StageTransitionTargetResolver.ConditionFact(revision,
                            SATISFIED))), StageTransitionTargetResolver.Status.INVALID_INPUT,
                    "INVALID_CONDITION_REVISION", "facts[0].revisionId");
        }
    }

    @Test
    void duplicateFactsAreRejectedEvenWhenTheyAgreeAndRegardlessOfOrdering() {
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", 11L, 1, false));
        for (StageTransitionTargetResolver.ConditionStatus second : StageTransitionTargetResolver.ConditionStatus.values()) {
            List<StageTransitionTargetResolver.ConditionFact> facts = new ArrayList<>(List.of(fact(11, SATISFIED),
                    fact(11, second)));
            for (int i = 0; i < 2; i++) {
                assertRejected(resolve(graph, "S0", facts), StageTransitionTargetResolver.Status.INVALID_INPUT,
                        "DUPLICATE_CONDITION_FACT", "facts[1].revisionId");
                Collections.reverse(facts);
            }
        }
    }

    @Test
    void inputPermutationsDoNotChangeResolvedAmbiguousUnavailableNoMatchOrDefaultResults() {
        List<StageTransitionDefinition> edges = List.of(edge("B", "S1", 11L, 1, false),
                edge("A", "S2", 12L, 1, false));
        List<List<StageTransitionTargetResolver.ConditionFact>> cases = List.of(
                List.of(fact(11, SATISFIED), fact(12, UNSATISFIED)),
                List.of(fact(11, SATISFIED), fact(12, SATISFIED)),
                List.of(fact(11, UNAVAILABLE), fact(12, UNAVAILABLE)),
                List.of(fact(11, UNSATISFIED), fact(12, UNSATISFIED)));
        for (boolean withDefault : new boolean[]{false, true}) {
            List<StageTransitionDefinition> configured = new ArrayList<>(edges);
            if (withDefault) {
                configured.add(edge("DEFAULT", "S2", null, 0, true));
            }
            StageTransitionGraph original = branches(configured.toArray(StageTransitionDefinition[]::new));
            for (List<StageTransitionTargetResolver.ConditionFact> facts : cases) {
                StageTransitionTargetResolver.Result expected = resolve(original, "S0", facts);
                for (List<StageTransitionGraph.Stage> stageOrder : permutations(original.stages())) {
                    for (List<StageTransitionDefinition> edgeOrder : permutations(configured)) {
                        for (List<StageTransitionTargetResolver.ConditionFact> factOrder : permutations(facts)) {
                            assertEquals(expected, resolve(new StageTransitionGraph(stageOrder, edgeOrder), "S0", factOrder));
                        }
                    }
                }
            }
        }
    }

    @Test
    void resolvingDoesNotMutateInputAndResultEvidenceIsImmutable() {
        List<StageTransitionTargetResolver.ConditionFact> facts = new ArrayList<>(List.of(fact(11, SATISFIED)));
        StageTransitionGraph graph = single(new StageTransitionDefinition("A", "S0", "S4", 11L, 1, false));
        StageTransitionGraph before = new StageTransitionGraph(graph.stages(), graph.transitions());
        StageTransitionTargetResolver.Result result = resolve(graph, "S0", facts);
        assertEquals(before, graph);
        assertEquals(List.of(fact(11, SATISFIED)), facts);
        facts.clear();
        assertResolved(result, "A", "S4");
        assertThrows(UnsupportedOperationException.class, () -> result.candidateTransitionCodes().clear());
        StageTransitionTargetResolver.Result rejected = resolve(graph, "S0", facts);
        assertThrows(UnsupportedOperationException.class, () -> rejected.failures().clear());
    }

    private static <T> List<List<T>> permutations(List<T> values) {
        if (values.isEmpty()) {
            return List.of(List.of());
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            List<T> rest = new ArrayList<>(values);
            T first = rest.remove(i);
            for (List<T> suffix : permutations(rest)) {
                List<T> order = new ArrayList<>();
                order.add(first);
                order.addAll(suffix);
                result.add(order);
            }
        }
        return result;
    }

    private static StageTransitionGraph.Stage stage(String code, boolean start, boolean closure) {
        return new StageTransitionGraph.Stage(code, start, closure);
    }

    private static StageTransitionDefinition edge(String code, String to, Long revision, int priority, boolean fallback) {
        return new StageTransitionDefinition(code, "S0", to, revision, priority, fallback);
    }

    private static StageTransitionTargetResolver.ConditionFact fact(long revision,
                                                                    StageTransitionTargetResolver.ConditionStatus status) {
        return new StageTransitionTargetResolver.ConditionFact(revision, status);
    }

    private static StageTransitionGraph single(StageTransitionDefinition... edges) {
        return new StageTransitionGraph(List.of(stage("S0", true, false), stage("S4", false, true)), Arrays.asList(edges));
    }

    private static StageTransitionGraph branches(StageTransitionDefinition... edges) {
        return new StageTransitionGraph(List.of(stage("S0", true, false), stage("S1", false, true),
                stage("S2", false, true)), Arrays.asList(edges));
    }

    private static void assertResolved(StageTransitionTargetResolver.Result result, String transition, String target) {
        assertEquals(StageTransitionTargetResolver.Status.RESOLVED, result.status());
        assertEquals(transition, result.transitionCode());
        assertEquals(target, result.targetStageCode());
        assertEquals(List.of(transition), result.candidateTransitionCodes());
        assertTrue(result.failures().isEmpty());
    }

    private static void assertTerminal(StageTransitionTargetResolver.Result result) {
        assertEquals(StageTransitionTargetResolver.Status.TERMINAL, result.status());
        assertNull(result.targetStageCode());
        assertNull(result.transitionCode());
        assertTrue(result.candidateTransitionCodes().isEmpty());
        assertTrue(result.failures().isEmpty());
    }

    private static void assertRejected(StageTransitionTargetResolver.Result result, StageTransitionTargetResolver.Status status,
                                       String failureCode, String path) {
        assertEquals(status, result.status());
        assertNull(result.targetStageCode());
        assertNull(result.transitionCode());
        assertFalse(result.failures().isEmpty());
        assertTrue(result.failures().stream().anyMatch(failure -> failureCode.equals(failure.code())
                        && path.equals(failure.path()) && failure.message() != null && !failure.message().isBlank()),
                () -> "缺少可定位失败项 " + failureCode + " @ " + path + "，实际：" + result.failures());
    }
}
