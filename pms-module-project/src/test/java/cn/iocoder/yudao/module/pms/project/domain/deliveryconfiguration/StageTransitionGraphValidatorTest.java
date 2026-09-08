package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PM-03 / F-PROJ-009 AC-02、AC-03：真实图结构及单一关系来源行为，不使用 Spring 或数据库。 */
class StageTransitionGraphValidatorTest {

    @Test
    void standardS0ToS6ChainPasses() {
        List<StageTransitionGraph.Stage> stages = new ArrayList<>();
        List<StageTransitionDefinition> transitions = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            stages.add(stage("S" + i, i == 0, i == 6));
            if (i < 6) {
                transitions.add(edge("T" + i, "S" + i, "S" + (i + 1)));
            }
        }
        assertValid(new StageTransitionGraph(stages, transitions));
    }

    @Test
    void presalesS0ToS4DoesNotRequireOtherStages() {
        StageTransitionGraph graph = presales();
        assertValid(graph);
        assertEquals(List.of("S0", "S4"), graph.stages().stream()
                .map(StageTransitionGraph.Stage::stageCode).toList());
    }

    @Test
    void everySubsetContainingS0CanHaveAnExplicitClosure() {
        // 64 种合法子集，包括仅有 S0 的终点图；没有隐式 S6 要求。
        for (int mask = 0; mask < 64; mask++) {
            List<String> codes = new ArrayList<>(List.of("S0"));
            for (int i = 1; i <= 6; i++) {
                if ((mask & (1 << (i - 1))) != 0) {
                    codes.add("S" + i);
                }
            }
            List<StageTransitionGraph.Stage> stages = new ArrayList<>();
            List<StageTransitionDefinition> transitions = new ArrayList<>();
            for (int i = 0; i < codes.size(); i++) {
                stages.add(stage(codes.get(i), i == 0, i == codes.size() - 1));
                if (i + 1 < codes.size()) {
                    transitions.add(edge("T" + i, codes.get(i), codes.get(i + 1)));
                }
            }
            assertValid(new StageTransitionGraph(stages, transitions));
        }
    }

    @Test
    void multipleExplicitClosuresAreAllowed() {
        assertValid(branches(new StageTransitionDefinition("A", "S0", "S1", 11L, 1, false),
                new StageTransitionDefinition("B", "S0", "S2", null, 0, true)));
    }

    @Test
    void graphDoesNotInferStageOrderFromCodes() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S2", false, true),
                stage("S4", false, false), stage("S0", true, false)),
                List.of(edge("SECOND", "S4", "S2"), edge("FIRST", "S0", "S4")));
        assertValid(graph);
        assertEquals("S4", graph.outgoing("S0").getFirst().toStageCode());
        assertEquals("S2", graph.outgoing("S4").getFirst().toStageCode());
    }

    @Test
    void incomingAndOutgoingViewsUseTheSameImmutableRelations() {
        List<StageTransitionGraph.Stage> stages = new ArrayList<>(presales().stages());
        List<StageTransitionDefinition> transitions = new ArrayList<>(presales().transitions());
        StageTransitionGraph graph = new StageTransitionGraph(stages, transitions);
        stages.clear();
        transitions.clear();
        assertValid(graph);
        assertEquals(graph.transitions(), graph.outgoing("S0"));
        assertEquals(graph.outgoing("S0"), graph.incoming("S4"));
        assertTrue(graph.incoming("S0").isEmpty());
        assertTrue(graph.outgoing("S4").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> graph.stages().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.transitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.outgoing("S0").clear());
    }

    @Test
    void reorderingStagesAndRelationsDoesNotChangeValidityOrViews() {
        StageTransitionGraph original = branches(
                new StageTransitionDefinition("B", "S0", "S1", 11L, 1, false),
                new StageTransitionDefinition("A", "S0", "S2", 12L, 1, false));
        List<StageTransitionGraph.Stage> stages = new ArrayList<>(original.stages());
        List<StageTransitionDefinition> transitions = new ArrayList<>(original.transitions());
        Collections.reverse(stages);
        Collections.reverse(transitions);
        StageTransitionGraph reordered = new StageTransitionGraph(stages, transitions);
        assertValid(original);
        assertValid(reordered);
        assertEquals(original.outgoing("S0"), reordered.outgoing("S0"));
        assertEquals(original.incoming("S1"), reordered.incoming("S1"));
    }

    @Test
    void nullGraphAndNullOrEmptyCollectionsReturnLocatedFailures() {
        assertFailure(null, "REQUIRED", "graph");
        assertFailure(new StageTransitionGraph(null, List.of()), "EMPTY_GRAPH", "stages");
        assertFailure(new StageTransitionGraph(List.of(), List.of()), "EMPTY_GRAPH", "stages");
        assertFailure(new StageTransitionGraph(presales().stages(), null), "REQUIRED", "transitions");
    }

    @Test
    void nullNodesAndRelationsAreNotReportedByNpe() {
        assertFailure(new StageTransitionGraph(Arrays.asList((StageTransitionGraph.Stage) null), List.of()),
                "REQUIRED", "stages[0]");
        assertFailure(new StageTransitionGraph(presales().stages(),
                Arrays.asList((StageTransitionDefinition) null)), "REQUIRED", "transitions[0]");
    }

    @Test
    void invalidStageCodesAreRejectedWithoutNormalization() {
        for (String code : Arrays.asList(null, "", " ", "S7", "S00", "s0", " S0")) {
            assertFailure(new StageTransitionGraph(List.of(stage(code, true, true)), List.of()),
                    "INVALID_STAGE_CODE", "stages[0].stageCode");
        }
    }

    @Test
    void duplicateStagesAreRejectedRatherThanCollapsed() {
        assertFailure(new StageTransitionGraph(List.of(stage("S0", true, true), stage("S0", false, true)),
                List.of()), "DUPLICATE_STAGE", "stages[1].stageCode");
    }

    @Test
    void startAndClosureFlagsMustBeExplicit() {
        StageTransitionGraph graph = new StageTransitionGraph(
                List.of(new StageTransitionGraph.Stage("S0", null, null)), List.of());
        assertFailure(graph, "REQUIRED", "stages[0].start");
        assertFailure(graph, "REQUIRED", "stages[0].normalClosure");
    }

    @Test
    void missingMultipleOrNonS0StartIsRejected() {
        assertFailure(new StageTransitionGraph(List.of(stage("S0", false, true)), List.of()),
                "START_COUNT", "stages.start");
        assertFailure(new StageTransitionGraph(List.of(stage("S0", true, false), stage("S4", true, true)),
                presales().transitions()), "START_COUNT", "stages.start");
        assertFailure(new StageTransitionGraph(List.of(stage("S4", true, true)), List.of()),
                "START_NOT_S0", "stages[S4].start");
    }

    @Test
    void closureIsNotInferredFromAnEmptyOutgoingList() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, false)), List.of());
        assertFailure(graph, "MISSING_CLOSURE", "stages.normalClosure");
        assertFailure(graph, "MISSING_OUTGOING", "stages[S0].outgoing");
    }

    @Test
    void nonClosureWithoutOutgoingIsRejectedEvenWithAnotherReachableClosure() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, false),
                stage("S1", false, false), stage("S2", false, true)), List.of(
                new StageTransitionDefinition("A", "S0", "S1", null, 1, false),
                new StageTransitionDefinition("B", "S0", "S2", null, 2, false)));
        assertFailure(graph, "MISSING_OUTGOING", "stages[S1].outgoing");
    }

    @Test
    void closureWithOutgoingIsRejected() {
        assertFailure(new StageTransitionGraph(List.of(stage("S0", true, true), stage("S4", false, true)),
                presales().transitions()), "CLOSURE_HAS_OUTGOING", "stages[S0].normalClosure");
    }

    @Test
    void danglingSourceAndTargetAreLocatedSeparately() {
        assertFailure(withEdges(edge("A", "S1", "S4")), "DANGLING_SOURCE", "transitions[0].fromStageCode");
        assertFailure(withEdges(edge("A", "S0", "S6")), "DANGLING_TARGET", "transitions[0].toStageCode");
        assertFailure(withEdges(edge("A", null, null)), "DANGLING_SOURCE", "transitions[0].fromStageCode");
        assertFailure(withEdges(edge("A", null, null)), "DANGLING_TARGET", "transitions[0].toStageCode");
    }

    @Test
    void selfLoopAndReachableCycleAreRejected() {
        assertFailure(withEdges(edge("SELF", "S0", "S0")), "SELF_LOOP", "transitions[0].toStageCode");
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, false),
                stage("S1", false, false), stage("S4", false, true)), List.of(edge("A", "S0", "S1"),
                edge("BACK", "S1", "S0"), new StageTransitionDefinition("EXIT", "S1", "S4", null, 2, false)));
        assertFailure(graph, "CYCLE", "transitions[BACK].toStageCode");
    }

    @Test
    void disconnectedCycleIsRejectedEvenIfStartReachesAClosure() {
        StageTransitionGraph graph = new StageTransitionGraph(List.of(stage("S0", true, false),
                stage("S1", false, false), stage("S2", false, false), stage("S4", false, true)),
                List.of(edge("EXIT", "S0", "S4"), edge("A", "S1", "S2"), edge("BACK", "S2", "S1")));
        assertFailure(graph, "CYCLE", "transitions[BACK].toStageCode");
        assertFailure(graph, "UNREACHABLE", "stages[S1].stageCode");
    }

    @Test
    void unreachableNodeAndUnreachableClosureAreRejected() {
        assertFailure(new StageTransitionGraph(List.of(stage("S0", true, true), stage("S4", false, true)),
                List.of()), "UNREACHABLE", "stages[S4].stageCode");
        assertFailure(new StageTransitionGraph(List.of(stage("S0", true, false), stage("S4", false, true)),
                List.of()), "NO_REACHABLE_CLOSURE", "stages.normalClosure");
    }

    @Test
    void blankDuplicateAndNullTransitionCodesAreRejected() {
        for (String code : Arrays.asList(null, "", " ")) {
            assertFailure(withEdges(edge(code, "S0", "S4")), "REQUIRED", "transitions[0].transitionCode");
        }
        assertFailure(withEdges(edge("A", "S0", "S4"), edge("A", "S0", "S4")),
                "DUPLICATE_TRANSITION", "transitions[1].transitionCode");
    }

    @Test
    void nullPriorityAndDefaultFlagAreRejected() {
        StageTransitionGraph graph = withEdges(new StageTransitionDefinition("A", "S0", "S4", null, null, null));
        assertFailure(graph, "REQUIRED", "transitions[0].priority");
        assertFailure(graph, "REQUIRED", "transitions[0].defaultBranch");
    }

    @Test
    void conditionRevisionMustBePositiveAndDefaultMustBeUnconditional() {
        for (long revision : new long[]{0, -1, Long.MIN_VALUE}) {
            assertFailure(withEdges(new StageTransitionDefinition("A", "S0", "S4", revision, 1, false)),
                    "INVALID_CONDITION_REVISION", "transitions[0].conditionRevisionId");
        }
        assertFailure(withEdges(new StageTransitionDefinition("A", "S0", "S4", 1L, 1, true)),
                "DEFAULT_HAS_CONDITION", "transitions[0].conditionRevisionId");
    }

    @Test
    void multipleDefaultsAtOneSourceAreRejectedRegardlessOfPriority() {
        assertFailure(branches(new StageTransitionDefinition("A", "S0", "S1", null, 1, true),
                        new StageTransitionDefinition("B", "S0", "S2", null, 2, true)),
                "MULTIPLE_DEFAULTS", "transitions[B].defaultBranch");
    }

    @Test
    void eachSourceMayHaveItsOwnDefault() {
        assertValid(new StageTransitionGraph(List.of(stage("S0", true, false), stage("S1", false, false),
                stage("S4", false, true)), List.of(new StageTransitionDefinition("A", "S0", "S1", null, 1, true),
                new StageTransitionDefinition("B", "S1", "S4", null, 1, true))));
    }

    @Test
    void unconditionalSamePriorityConflictIsRejectedBeforeRuntime() {
        assertFailure(branches(edge("A", "S0", "S1"), edge("B", "S0", "S2")),
                "UNCONDITIONAL_PRIORITY_CONFLICT", "transitions[B].priority");
    }

    @Test
    void distinctUnconditionalPrioritiesAndConditionalTiesAreStructurallyValid() {
        assertValid(branches(new StageTransitionDefinition("A", "S0", "S1", null, 1, false),
                new StageTransitionDefinition("B", "S0", "S2", null, 2, false)));
        assertValid(branches(new StageTransitionDefinition("A", "S0", "S1", 11L, 1, false),
                new StageTransitionDefinition("B", "S0", "S2", 12L, 1, false)));
    }

    @Test
    void invalidGraphOrUnknownViewNodeFailsExplicitly() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> new StageTransitionGraph(null, null).outgoing("S0"));
        assertTrue(invalid.getMessage().contains("stages"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> presales().incoming(null))
                .getMessage().contains("stageCode"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> presales().outgoing("S1"))
                .getMessage().contains("stageCode"));
    }

    private static StageTransitionGraph.Stage stage(String code, boolean start, boolean closure) {
        return new StageTransitionGraph.Stage(code, start, closure);
    }

    private static StageTransitionDefinition edge(String code, String from, String to) {
        return new StageTransitionDefinition(code, from, to, null, 1, false);
    }

    private static StageTransitionGraph presales() {
        return withEdges(edge("S0_S4", "S0", "S4"));
    }

    private static StageTransitionGraph withEdges(StageTransitionDefinition... transitions) {
        return new StageTransitionGraph(List.of(stage("S0", true, false), stage("S4", false, true)),
                Arrays.asList(transitions));
    }

    private static StageTransitionGraph branches(StageTransitionDefinition... transitions) {
        return new StageTransitionGraph(List.of(stage("S0", true, false), stage("S1", false, true),
                stage("S2", false, true)), Arrays.asList(transitions));
    }

    private static void assertValid(StageTransitionGraph graph) {
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        assertTrue(failures.isEmpty(), () -> "图应有效，实际失败项：" + failures);
    }

    private static void assertFailure(StageTransitionGraph graph, String code, String path) {
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        assertFalse(failures.isEmpty());
        assertTrue(failures.stream().anyMatch(failure -> code.equals(failure.code()) && path.equals(failure.path())
                        && failure.message() != null && !failure.message().isBlank()),
                () -> "缺少可定位失败项 " + code + " @ " + path + "，实际：" + failures);
    }
}
