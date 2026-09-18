package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import cn.iocoder.yudao.module.pms.project.domain.rule.ResultEvidenceReceipt;
import cn.iocoder.yudao.module.pms.project.domain.rule.StageCompletionEvidence;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy.Accumulator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 生产证据Mapper、Guard与事务代理，Owner事实和项目仓储沿用显式外围替身。 */
class ProjectResultEvidenceGuardTest {
    private ResultEvidenceScanFixture f;
    private ProjectResultEvidenceGuard guard;
    private TransactionTemplate tx;
    @BeforeEach void before() throws Exception {
        f=new ResultEvidenceScanFixture();tx=new TransactionTemplate(f.recovery.transactions);
        guard=f.recovery.proxy(new ProjectResultEvidenceGuard(f.recovery.subscriptions,f.evidence,
                new ProjectResultEvidenceConsistency(f.recovery.sources,f.recovery.journal),f.recovery.outbox));
    }
    @AfterEach void after(){f.close();}
    private ProjectResultEvidenceGuard.Proof check(){return tx.execute(status->guard.lock(f.recovery.snapshot,f.recovery.plan,f.recovery.round));}

    @Test void onlyACompleteSatisfiedScanCanBePinnedIntoTheCurrentRound() {
        assertFalse(check().ready());f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        var proof=check();assertTrue(proof.ready());assertEquals(1,proof.receipts().size());
        var ref=proof.receipts().getFirst();assertEquals(f.scan().getId(),ref.scanId());assertEquals(20,ref.executionId());
        assertEquals(30,ref.contractId());assertEquals(7,ref.baselineSequence());assertEquals(7,ref.throughSequence());
        assertThrows(UnsupportedOperationException.class,()->proof.receipts().clear());
        assertEquals(proof,check());
    }
    @Test void changedSourceBeforeCompletionRequestsRecoveryAndCannotUseTheOldConclusion() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();var old=f.scan();f.recovery.committed=8;
        assertEquals("RESULT_EVIDENCE_CHANGED",check().reason());assertFalse(check().ready());
        assertEquals(old.getId(),f.scan().getId());assertEquals(7,f.recovery.row().getProcessedSequence());
        assertTrue(f.events(ResultSubscriptionWakeup.EVENT_TYPE)>0);
    }
    @Test void allConfiguredSubscriptionsMustBeReadyAndPartialProofsAreNotReturned() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        var array=(tools.jackson.databind.node.ArrayNode)f.recovery.snapshot.getStages().getFirst().getExecution().get("subscriptions");
        var other=(tools.jackson.databind.node.ObjectNode)array.get(0).deepCopy();other.put("key","another");array.add(other);
        assertEquals("RESULT_SUBSCRIPTION_NOT_INSTALLED",check().reason());assertTrue(check().receipts().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"INVENTORY","CHANGES"})
    void incompleteRecoveryCannotExposeAnOldSatisfiedScan(String phase) {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        f.recovery.jdbc.update("UPDATE proj_result_subscription SET phase=?,through_sequence=?",phase,"CHANGES".equals(phase)?7L:null);
        assertEquals("RESULT_SUBSCRIPTION_RECOVERING",check().reason());
    }
    @ParameterizedTest @ValueSource(strings={"tenant","project","plan","contract","node","configuration","retired"})
    void wrongSubscriptionIdentityNeverBorrowsAnotherProof(String damage) {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        switch(damage){
            case "tenant" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET tenant_id=2");
            case "project" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET project_id=4");
            case "plan" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET plan_version_id=11");
            case "contract" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET contract_id=31");
            case "node" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET node_id=5");
            case "configuration" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET configuration=REPLACE(configuration,'EXACT_ONE','ANY_MATCHING')");
            case "retired" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET phase='RETIRED'");
            default -> throw new AssertionError(damage);
        }
        if(Set.of("tenant","project","plan").contains(damage))assertFalse(check().ready());
        else assertThrows(IllegalStateException.class,this::check);
    }
    @Test void changedEpochAndCorruptFinalConclusionCannotUseStatusAloneAsProof() {
        f.seed(1,"o","r",null,Validity.CURRENT);f.tick();
        f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET accumulator=?",JsonUtils.toJsonString(Accumulator.empty()));
        assertThrows(IllegalStateException.class,this::check);
        f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET status='UNKNOWN_FORMAT'");
        assertThrows(IllegalStateException.class,this::check);
    }
    @Test void delayedCollectingAndUnavailableProofsDoNotBecomeZeroRequirementSuccess() {
        for(int i=1;i<=101;i++)f.seed(i,"o"+i,"r"+i,null,Validity.CURRENT);
        f.tick();assertEquals("RESULT_EVIDENCE_COLLECTING",check().reason());f.tick();
        assertEquals("RESULT_EVIDENCE_AMBIGUOUS",check().reason());
    }
    @Test void legacyNodeHasNoNewPersistenceOrSourceDependency() {
        f.recovery.snapshot.getStages().getFirst().setExecution(null);clearInvocations(f.recovery.journal,f.recovery.sources);
        assertTrue(check().ready());assertTrue(check().receipts().isEmpty());
        verifyNoInteractions(f.recovery.journal,f.recovery.sources);
    }
    @Test void stageEvidenceKeepsOldSerializationAndNewReferencesAreImmutable() {
        var old=new StageCompletionEvidence(20L,10L,null,null,List.of(),null,null);
        assertFalse(JsonUtils.parseTree(JsonUtils.toJsonString(old)).has("subscriptionEvidence"));
        var refs=new ArrayList<ResultEvidenceReceipt>();refs.add(new ResultEvidenceReceipt(501L,"s",601L,10L,20L,30L,2,7,9));
        var current=new StageCompletionEvidence(20L,10L,null,null,List.of(),null,null,refs);refs.clear();
        assertEquals(1,current.subscriptionEvidence().size());
        assertEquals(current,JsonUtils.parseObject(JsonUtils.toJsonString(current),StageCompletionEvidence.class));
    }
}
