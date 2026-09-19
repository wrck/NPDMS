package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultEvidenceMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy.Accumulator;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResultEvidenceScannerTest {
    private ResultEvidenceScanFixture f;
    @BeforeEach void before() throws Exception {f=new ResultEvidenceScanFixture();}
    @AfterEach void after(){f.close();}

    @Test void noCandidatesProducesWaitingNotSuccessAndDoesNotRepeatedlyPublishTheSameConclusion() {
        f.tick();assertEquals("WAITING",f.scan().getStatus());assertEquals(0,f.items());
        f.tick();assertEquals(1,f.events(ResultEvidenceEvaluatedEvent.EVENT_TYPE));
    }
    @Test void exactOneCannotFinishBeforeTheLastCandidatePage() {
        for(int i=1;i<=101;i++)f.seed(i,"obj"+i,"r"+i,null,i==101?Validity.CURRENT:Validity.NOT_CURRENT);
        f.tick();assertEquals("COLLECTING",f.scan().getStatus());assertEquals(100,f.items());assertEquals(0,f.events(ResultEvidenceEvaluatedEvent.EVENT_TYPE));
        f.tick();assertEquals("SATISFIED",f.scan().getStatus());assertEquals(101,f.items());
        assertEquals(1,JsonUtils.parseObject(f.scan().getAccumulator(),Accumulator.class).eligible());
        assertEquals(7,f.scan().getThroughSequence());
    }
    @Test void moreThanOneExactResultIsAmbiguousEvenWhenBothBelongToOneObject() {
        f.policy("REUSE_EXISTING","HISTORICAL_FACT","EXACT_ONE",null);
        f.seed(1,"obj","r1",null,Validity.NOT_CURRENT);f.seed(2,"obj","r2",null,Validity.CURRENT);
        f.tick();assertEquals("AMBIGUOUS",f.scan().getStatus());assertEquals(2,f.items());
    }
    @Test void anyMatchingPreservesEveryQualifiedReferenceRatherThanSelectingTheFirst() {
        f.policy("REUSE_EXISTING","CURRENT_VALID","ANY_MATCHING",null);
        for(int i=1;i<=150;i++)f.seed(i,"obj"+i,"r"+i,null,Validity.CURRENT);
        f.tick();f.tick();assertEquals("SATISFIED",f.scan().getStatus());assertEquals(150,f.items());
        assertEquals(150,JsonUtils.parseObject(f.scan().getAccumulator(),Accumulator.class).eligible());
    }
    @Test void allExpectedWaitsForTheCompleteFrozenObjectSetAcrossPages() {
        f.policy("REUSE_EXISTING","HISTORICAL_FACT","ALL_EXPECTED",List.of("a","b"));
        for(int i=1;i<=100;i++)f.seed(i,"a","r"+i,null,Validity.NOT_CURRENT);
        f.seed(101,"b","r101",null,Validity.CURRENT);
        f.tick();assertEquals("COLLECTING",f.scan().getStatus());f.tick();assertEquals("SATISFIED",f.scan().getStatus());
        assertEquals(Set.of("a","b"),JsonUtils.parseObject(f.scan().getAccumulator(),Accumulator.class).coveredObjects());
    }
    @Test void onlyCommittedFirstFormationAfterTheOriginalRoundCanSatisfyNewResult() {
        f.policy("NEW_RESULT","CURRENT_VALID","EXACT_ONE",null);f.advanceEpoch(11);
        f.seed(1,"obj","r1",7L,Validity.CURRENT);f.seed(2,"obj","r2",8L,Validity.CURRENT);f.seed(3,"obj","r3",null,Validity.CURRENT);
        f.tick();assertEquals("SATISFIED",f.scan().getStatus());assertEquals(1,JsonUtils.parseObject(f.scan().getAccumulator(),Accumulator.class).eligible());
        assertEquals(7,f.recovery.row().getBaselineSequence());
    }
    @Test void sourceChangesBetweenPagesCannotProduceAMixedEpochConclusion() {
        for(int i=1;i<=101;i++)f.seed(i,"o"+i,"r"+i,null,Validity.CURRENT);
        f.tick();var original=f.scan();assertEquals("COLLECTING",original.getStatus());
        f.recovery.committed=8;f.tick();assertEquals(100,f.items());assertEquals(original.getVersion(),f.scan().getVersion());
        assertTrue(f.events(ResultSubscriptionWakeup.EVENT_TYPE)>0);
        f.advanceEpoch(8);f.tick();f.tick();assertEquals("AMBIGUOUS",f.scan().getStatus());assertNotEquals(original.getId(),f.scan().getId());
        assertEquals("COLLECTING",f.evidence.selectById(new ResultEvidenceMapper.ScanId(1L,3L,original.getId())).getStatus());
    }
    @Test void unavailableOwnerRollsBackTheEvidencePageAndRetryReadsTheOwnerAgain() {
        f.seed(1,"a","r1",null,Validity.CURRENT);f.seed(2,"b","r2",null,Validity.CURRENT);
        f.observations.put("r2",Observation.absent(Status.UNAVAILABLE,"OFFLINE"));
        assertThrows(IllegalStateException.class,f::tick);assertNull(f.scan());assertEquals(0,f.items());
        f.observations.put("r2",f.recovery.result("b","r2",Validity.NOT_CURRENT));f.tick();assertEquals("SATISFIED",f.scan().getStatus());
    }
    @Test void failedOutboxDoesNotLeaveAFinalizedScanOrEvidenceWithoutItsNotification() {
        f.seed(1,"a","r1",null,Validity.CURRENT);f.recovery.failOutbox=true;
        assertThrows(IllegalStateException.class,f::tick);assertNull(f.scan());assertEquals(0,f.items());
        f.recovery.failOutbox=false;f.tick();assertEquals("SATISFIED",f.scan().getStatus());
    }
    @Test void revokedHistoricalFactIsNotQualifiedWhileASupersededFactCanBe() {
        f.policy("REUSE_EXISTING","HISTORICAL_FACT","EXACT_ONE",null);
        f.seed(1,"a","r1",null,Validity.NOT_CURRENT);f.seed(2,"a","r2",null,Validity.REVOKED);
        f.tick();assertEquals("SATISFIED",f.scan().getStatus());assertEquals(1,JsonUtils.parseObject(f.scan().getAccumulator(),Accumulator.class).eligible());
    }
    @Test void immutableFinalConclusionsCannotBeAdvancedOrOverwritten() {
        f.seed(1,"a","r1",null,Validity.CURRENT);f.tick();var scan=f.scan();
        assertEquals(0,f.evidence.advance(new ResultEvidenceMapper.Progress(1L,3L,scan.getId(),scan.getVersion(),1001,"{}","WAITING")));
        assertEquals("SATISFIED",f.scan().getStatus());f.tick();assertEquals(1,f.items());
    }
    @Test void unprovenCommitCoverageAndDowngradedHistoricalCapabilityCannotBeUsedAsEvidence() {
        when(f.recovery.sources.commitBarrierSupported(any())).thenReturn(false);
        assertThrows(IllegalStateException.class,f::tick);assertNull(f.scan());
        when(f.recovery.sources.commitBarrierSupported(any())).thenReturn(true);
        f.policy("PINNED_RESULT","CURRENT_VALID","EXACT_ONE",null);
        when(f.recovery.sources.descriptor(any())).thenReturn(new Descriptor(ResultSubscriptionRecoveryFixture.TYPE,true,false,false));
        assertThrows(IllegalStateException.class,f::tick);assertNull(f.scan());verify(f.recovery.sources,never()).inspect(any());
    }
    @Test void anOldEventCannotBeReinterpretedAsTheNewSubscriptionCheckpoint() {
        var event=ResultEvidenceScanEvent.create(f.recovery.row(),f.recovery.row().getVersion(),"old");
        f.advanceEpoch(8);f.scanner.process(event);assertNull(f.scan());
        assertThrows(IllegalArgumentException.class,() -> f.scanner.process(ResultEvidenceScanEvent.create(f.recovery.row(),99,"future")));
    }
    @Test void changedNativeObservationIsCheckedInsteadOfTrustingTheStoredCandidate() {
        f.seed(1,"a","r1",null,Validity.CURRENT);f.observations.put("r1",Observation.absent(Status.NOT_FOUND,"REMOVED"));
        f.tick();assertEquals("WAITING",f.scan().getStatus());
        var item=f.evidence.selectItems(new ResultEvidenceMapper.Items(1L,3L,f.scan().getId(),0,10)).getFirst();
        assertEquals("INELIGIBLE",item.getEligibility());assertEquals("REMOVED",item.getReason());
    }
}
