package cn.iocoder.yudao.module.pms.platform.support.result;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSelectionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort.DecisionStatus;
import cn.iocoder.yudao.module.pms.platform.support.result.ResultSelectionEvaluator.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResultSelectionEvaluatorTest {

    private static ResultSelectionPolicy policy(String acquisition, String validity, String selection) {
        return new ResultSelectionPolicy(ResultSelectionPolicy.Acquisition.valueOf(acquisition),
                ResultSelectionPolicy.Selection.valueOf(selection),
                ResultSelectionPolicy.Validity.valueOf(validity),
                "PINNED_RESULT".equals(acquisition) ? "R1" : null, List.of(11L, 12L));
    }

    private static Candidate candidate(long object, String result, long formation, boolean valid) {
        return new Candidate(object, result, formation, valid);
    }

    private static Qualification qualify(ResultSelectionPolicy policy, Candidate value) {
        return ResultSelectionEvaluator.qualify(policy, 10, 20, value);
    }

    @ParameterizedTest @CsvSource({
            "REUSE_EXISTING,CURRENT_VALID,ELIGIBLE", "REUSE_EXISTING,ANY,ELIGIBLE",
            "NEW_RESULT,CURRENT_VALID,ELIGIBLE", "NEW_RESULT,ANY,ELIGIBLE",
            "PINNED_RESULT,CURRENT_VALID,ELIGIBLE", "PINNED_RESULT,ANY,ELIGIBLE"})
    void acquisitionAndValidityRemainIndependent(String acquisition, String validity, Eligibility expected) {
        assertEquals(expected, qualify(policy(acquisition, validity, "EXACT_ONE"),
                candidate(11L, "R1", 11L, true)).eligibility());
    }

    @Test
    void revokedAndStaleResultsNeverQualify() {
        var reuse = policy("REUSE_EXISTING", "CURRENT_VALID", "EXACT_ONE");
        // 撤销结果在任何获取语义下都不是证据。
        assertEquals(Eligibility.INELIGIBLE, qualify(reuse, candidate(11L, "R1", 11L, false)).eligibility());
        assertEquals("RESULT_REVOKED", qualify(reuse, candidate(11L, "R1", 11L, false)).reason());
    }

    @Test
    void sameObjectNewResultCanQualifyButOldResultNotificationCannot() {
        var sub = policy("NEW_RESULT", "CURRENT_VALID", "EXACT_ONE");
        // 基线序号（含）之前形成的旧结果不能作为新结果证据。
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, candidate(11L, "R1", 10L, true)).eligibility());
        assertEquals("RESULT_FORMED_BEFORE_ROUND", qualify(sub, candidate(11L, "R1", 10L, true)).reason());
        assertEquals(Eligibility.ELIGIBLE, qualify(sub, candidate(11L, "R2", 11L, true)).eligibility());
    }

    @Test
    void aPinnedResultNeverFallsBackToTheLatestResultOrAnotherObject() {
        var sub = policy("PINNED_RESULT", "CURRENT_VALID", "ANY_MATCHING");
        assertEquals("RESULT_NOT_PINNED", qualify(sub, candidate(11L, "R2", 11L, true)).reason());
        assertEquals(Eligibility.ELIGIBLE, qualify(sub, candidate(11L, "R1", 11L, true)).eligibility());
    }

    @ParameterizedTest @CsvSource({"EXACT_ONE,1,SATISFIED", "EXACT_ONE,2,AMBIGUOUS", "ANY_MATCHING,1,SATISFIED",
            "ANY_MATCHING,2,SATISFIED", "ALL_EXPECTED,1,WAITING", "ALL_EXPECTED,2,SATISFIED"})
    void selectionCountsTheCompleteResultSetWithoutSilentlyTakingTheFirst(String selection, int count,
                                                                          DecisionStatus expected) {
        var sub = policy("REUSE_EXISTING", "CURRENT_VALID", selection);
        var state = ResultSelectionEvaluator.accumulate(sub, Accumulator.empty(),
                List.of(qualify(sub, candidate(11L, "R1", 11L, true))));
        assertEquals(DecisionStatus.COLLECTING, ResultSelectionEvaluator.decide(sub, state, false).status(),
                "集合未读完只报收集中的结论");
        if (count == 2) {
            state = ResultSelectionEvaluator.accumulate(sub, state,
                    List.of(qualify(sub, candidate(12L, "R2", 11L, true))));
        }
        assertEquals(expected, ResultSelectionEvaluator.decide(sub, state, true).status());
    }

    @Test
    void allExpectedRequiresEveryExpectedObjectNotEveryResultInOnePage() {
        var sub = policy("REUSE_EXISTING", "ANY", "ALL_EXPECTED");
        var state = ResultSelectionEvaluator.accumulate(sub, Accumulator.empty(),
                List.of(qualify(sub, candidate(11L, "R1", 5L, true)),
                        qualify(sub, candidate(11L, "R2", 6L, true))));
        assertEquals(Set.of(12L), ResultSelectionEvaluator.decide(sub, state, true).missingObjects());
        assertFalse(ResultSelectionEvaluator.decide(sub, state, true).satisfied());
        var next = ResultSelectionEvaluator.accumulate(sub, state,
                List.of(qualify(sub, candidate(12L, "R3", 7L, true))));
        assertTrue(ResultSelectionEvaluator.decide(sub, next, true).satisfied());
        assertEquals(2, state.eligible(), "同对象多结果按身份分别计数");
    }

    @Test
    void emptyExpectedSetIsAContractErrorNotASilentSatisfaction() {
        var sub = new ResultSelectionPolicy(ResultSelectionPolicy.Acquisition.REUSE_EXISTING,
                ResultSelectionPolicy.Selection.ALL_EXPECTED, ResultSelectionPolicy.Validity.CURRENT_VALID,
                null, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ResultSelectionEvaluator.decide(sub, Accumulator.empty(), true));
    }

    @Test
    void existentialSelectionDoesNotRetainAnUnboundedObjectSet() {
        var sub = new ResultSelectionPolicy(ResultSelectionPolicy.Acquisition.REUSE_EXISTING,
                ResultSelectionPolicy.Selection.ANY_MATCHING, ResultSelectionPolicy.Validity.CURRENT_VALID,
                null, List.of());
        var accumulated = Accumulator.empty();
        for (int i = 0; i < 250; i++) {
            accumulated = ResultSelectionEvaluator.accumulate(sub, accumulated,
                    List.of(qualify(sub, candidate(i + 1L, "r" + i, 11L, true))));
        }
        assertTrue(accumulated.coveredObjects().isEmpty(), "存在性选择不为无限对象集保留覆盖清单");
        assertEquals(250, accumulated.eligible());
        assertTrue(ResultSelectionEvaluator.decide(sub, accumulated, true).satisfied());
    }

    @Test
    void rejectsCorruptFormationBoundariesAndDuplicateResults() {
        var sub = policy("NEW_RESULT", "CURRENT_VALID", "EXACT_ONE");
        for (long boundary : new long[]{-1, 0, 21, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> qualify(sub, candidate(11L, "R1", boundary, true)));
        }
        var item = qualify(sub, candidate(11L, "R1", 11L, true));
        assertThrows(IllegalArgumentException.class, () -> ResultSelectionEvaluator
                .accumulate(sub, Accumulator.empty(), List.of(item, item)), "同页重复原生结果身份必须拒绝");
    }
}
