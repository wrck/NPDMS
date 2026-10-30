package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ResultEvidencePolicyTest {
    private static final Type TYPE = new Type("OWNER", "ENTITY", "COMPLETED");
    private Subscription subscription(String acquisition, String validity, String selection) {
        return new Subscription("evidence", "OWNER", "ENTITY", "COMPLETED", new Scope("OBJECTS", List.of("A", "B")),
                new Policy(acquisition, validity, selection, "PINNED_RESULT".equals(acquisition) ? "R1" : null));
    }
    private Candidate candidate(String object, String result, Long formation, Validity validity) {
        return new Candidate(object, result, formation, Observation.available(new Result(1L, 2L, TYPE, object, result, null, "native-version", validity,
                LocalDateTime.of(2001, 1, 1, 0, 0))));
    }
    private Qualification qualify(Subscription sub, Candidate value) { return ResultEvidencePolicy.qualify(sub, 10, 20, value); }

    @ParameterizedTest @CsvSource({
        "REUSE_EXISTING,CURRENT_VALID,CURRENT,ELIGIBLE", "REUSE_EXISTING,CURRENT_VALID,NOT_CURRENT,INELIGIBLE",
        "REUSE_EXISTING,HISTORICAL_FACT,NOT_CURRENT,ELIGIBLE", "REUSE_EXISTING,HISTORICAL_FACT,REVOKED,INELIGIBLE",
        "REUSE_EXISTING,CURRENT_VALID,REVOKED,INELIGIBLE", "NEW_RESULT,CURRENT_VALID,CURRENT,ELIGIBLE",
        "NEW_RESULT,HISTORICAL_FACT,NOT_CURRENT,ELIGIBLE", "NEW_RESULT,HISTORICAL_FACT,REVOKED,INELIGIBLE",
        "PINNED_RESULT,CURRENT_VALID,CURRENT,ELIGIBLE", "PINNED_RESULT,HISTORICAL_FACT,NOT_CURRENT,ELIGIBLE",
        "PINNED_RESULT,HISTORICAL_FACT,REVOKED,INELIGIBLE"})
    void acquisitionAndValidityRemainIndependent(String acquisition, String validity, Validity state, Eligibility expected) {
        assertEquals(expected, qualify(subscription(acquisition, validity, "EXACT_ONE"), candidate("A", "R1", 11L, state)).eligibility());
    }
    @Test void sameObjectNewRevisionCanQualifyButNewNotificationOfAnOldResultCannot() {
        var sub = subscription("NEW_RESULT", "CURRENT_VALID", "EXACT_ONE");
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, candidate("A", "R1", 10L, Validity.CURRENT)).eligibility());
        assertEquals(Eligibility.ELIGIBLE, qualify(sub, candidate("A", "R2", 11L, Validity.CURRENT)).eligibility());
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, candidate("A", "R2", null, Validity.CURRENT)).eligibility());
    }
    @Test void nativeFormationTimeAndMissingBusinessRevisionDoNotInventANewResultBoundary() {
        var sub = subscription("NEW_RESULT", "CURRENT_VALID", "ANY_MATCHING");
        var input = candidate("A", "R1", null, Validity.CURRENT);
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, input).eligibility());
        assertNull(input.observation().result().businessRevision());
        assertEquals(Eligibility.ELIGIBLE, qualify(sub, candidate("A", "R1", 20L, Validity.CURRENT)).eligibility());
    }
    @Test void aPinnedResultNeverFallsBackToTheLatestRevisionOrAnotherObject() {
        var sub = subscription("PINNED_RESULT", "CURRENT_VALID", "ANY_MATCHING");
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, candidate("A", "R2", null, Validity.CURRENT)).eligibility());
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, candidate("outside", "R1", null, Validity.CURRENT)).eligibility());
        var replaced = new Candidate("A", "R1", null, candidate("A", "R2", null, Validity.CURRENT).observation());
        assertEquals(Eligibility.INELIGIBLE, qualify(sub, replaced).eligibility());
    }
    @ParameterizedTest @CsvSource({"EXACT_ONE,1,SATISFIED", "EXACT_ONE,2,AMBIGUOUS", "ANY_MATCHING,1,SATISFIED", "ANY_MATCHING,2,SATISFIED",
                                   "ALL_EXPECTED,1,WAITING", "ALL_EXPECTED,2,SATISFIED"})
    void selectionCountsTheCompleteResultSetWithoutSilentlyTakingTheFirst(String selection, int count, ResultEvidencePolicy.Status expected) {
        var sub = subscription("REUSE_EXISTING", "CURRENT_VALID", selection);
        var first = qualify(sub, candidate("A", "R1", null, Validity.CURRENT));
        var state = ResultEvidencePolicy.accumulate(sub, Accumulator.empty(), List.of(first));
        assertEquals(ResultEvidencePolicy.Status.COLLECTING, ResultEvidencePolicy.decide(sub, state, false).status());
        if (count == 2) state = ResultEvidencePolicy.accumulate(sub, state, List.of(qualify(sub, candidate("B", "R2", null, Validity.CURRENT))));
        assertEquals(expected, ResultEvidencePolicy.decide(sub, state, true).status());
    }
    @Test void allExpectedRequiresEveryFrozenObjectNotEveryResultInOnePage() {
        var sub = subscription("REUSE_EXISTING", "HISTORICAL_FACT", "ALL_EXPECTED");
        var state = ResultEvidencePolicy.accumulate(sub, Accumulator.empty(), List.of(qualify(sub, candidate("A","R1",null,Validity.NOT_CURRENT)),
                qualify(sub, candidate("A","R2",null,Validity.CURRENT))));
        assertEquals(Set.of("B"), ResultEvidencePolicy.decide(sub,state,true).missingObjects());
        assertFalse(ResultEvidencePolicy.decide(sub,state,true).satisfied());
        var next = ResultEvidencePolicy.accumulate(sub,state,List.of(qualify(sub,candidate("B","R3",null,Validity.CURRENT))));
        assertTrue(ResultEvidencePolicy.decide(sub,next,true).satisfied());assertEquals(2,state.eligible());
    }
    @Test void unavailableSourceIsNotAnEmptySuccessfulOrDefinitivelyFailedFact() {
        var sub = subscription("REUSE_EXISTING", "CURRENT_VALID", "ANY_MATCHING");
        var unavailable = new Candidate("B","R2",null,Observation.absent(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Status.UNAVAILABLE,"OFFLINE"));
        var state = ResultEvidencePolicy.accumulate(sub,Accumulator.empty(),List.of(qualify(sub,candidate("A","R1",null,Validity.CURRENT)),qualify(sub,unavailable)));
        assertEquals(ResultEvidencePolicy.Status.UNAVAILABLE, ResultEvidencePolicy.decide(sub,state,true).status());
    }
    @Test void missingAndUnformedNativeResultsCannotBecomeEvidence() {
        var sub = subscription("REUSE_EXISTING", "CURRENT_VALID", "EXACT_ONE");
        for (var status : List.of(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Status.NOT_FOUND,
                cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Status.NOT_FORMED)) {
            assertEquals(Eligibility.INELIGIBLE,qualify(sub,new Candidate("A","R1",null,Observation.absent(status,"MISSING"))).eligibility());
        }
        assertEquals(ResultEvidencePolicy.Status.WAITING,ResultEvidencePolicy.decide(sub,Accumulator.empty(),true).status());
    }
    @Test void rejectsCorruptFormationBoundariesDuplicateResultsAndEmptyExpectedSets() {
        var sub = subscription("NEW_RESULT", "CURRENT_VALID", "EXACT_ONE");
        for (long boundary : new long[]{-1,0,21,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,() -> qualify(sub,candidate("A","R1",boundary,Validity.CURRENT)));
        var item = qualify(sub,candidate("A","R1",11L,Validity.CURRENT));
        assertThrows(IllegalArgumentException.class,() -> ResultEvidencePolicy.accumulate(sub,Accumulator.empty(),List.of(item,item)));
        var invalid = new Subscription("x","OWNER","ENTITY","COMPLETED",new Scope("PROJECT",List.of()),new Policy("REUSE_EXISTING","CURRENT_VALID","ALL_EXPECTED",null));
        assertThrows(IllegalArgumentException.class,() -> ResultEvidencePolicy.decide(invalid,Accumulator.empty(),true));
        assertThrows(IllegalArgumentException.class,() -> new Accumulator(0,1,Set.of(),false));
    }
    @Test void projectScopeDoesNotRetainAnUnboundedObjectSetForExistentialSelection() {
        var sub = new Subscription("x","OWNER","ENTITY","COMPLETED",new Scope("PROJECT",List.of()),new Policy("REUSE_EXISTING","CURRENT_VALID","ANY_MATCHING",null));
        var accumulated = Accumulator.empty();
        for (int i=0;i<250;i++) accumulated=ResultEvidencePolicy.accumulate(sub,accumulated,List.of(qualify(sub,candidate("object"+i,"r"+i,null,Validity.CURRENT))));
        assertTrue(accumulated.coveredObjects().isEmpty());assertEquals(250,accumulated.eligible());
        assertTrue(ResultEvidencePolicy.decide(sub,accumulated,true).satisfied());
    }
}
