package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.InventoryPage;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResultSubscriptionRecoveryTest {
    private ResultSubscriptionRecoveryFixture f;
    @BeforeEach void before() throws Exception { f = new ResultSubscriptionRecoveryFixture(); }
    @AfterEach void after() { f.close(); }

    @Test void inventoryAndCommittedChangesShareOneRecoverableCheckpointWithoutCommandsOrPages() {
        var first = f.result("object-1", "result-1", Validity.CURRENT);
        var second = f.result("object-2", "result-2", Validity.CURRENT);
        when(f.sources.inventory(any())).thenReturn(new InventoryPage("a",false,List.of(first)), new InventoryPage("b",true,List.of(second)));
        f.tick(); assertEquals("INVENTORY",f.row().getPhase());assertEquals("a",f.row().getInventoryCursor());
        f.committed = 9;
        f.changes.add(f.change(8,first,true)); f.changes.add(f.change(9,second,false));
        f.tick();assertEquals("CHANGES",f.row().getPhase());assertEquals(9,f.row().getThroughSequence());
        f.committed = 10; // 新通知不扩大正在进行的补采页。
        f.tick();assertEquals("LIVE",f.row().getPhase());assertEquals(9,f.row().getProcessedSequence());assertEquals(7,f.row().getBaselineSequence());
        assertEquals(2,f.found().size());assertEquals(8,f.found().getFirst().getFormationSequence());assertNull(f.found().getLast().getFormationSequence());
        assertNull(f.snapshot.getStages().getFirst().getBinding());
        verify(f.journal).read(argThat(boundary -> boundary.sequence()==9),eq(7L),eq(100));
    }

    @Test void outboxFailureRollsBackTheWholeCandidatePageAndPosition() {
        when(f.sources.inventory(any())).thenReturn(new InventoryPage("a",false,List.of(f.result("obj","result",Validity.CURRENT))));
        f.failOutbox = true;
        assertThrows(IllegalStateException.class,f::tick);
        assertTrue(f.found().isEmpty());assertNull(f.row().getInventoryCursor());assertEquals(0,f.row().getVersion());
        f.failOutbox = false;f.tick();assertEquals(1,f.found().size());assertEquals("a",f.row().getInventoryCursor());
    }

    @Test void aBadLaterObservationRollsBackEarlierCandidatesInTheSamePage() {
        when(f.sources.inventory(any())).thenReturn(new InventoryPage("a",true,List.of(f.result("obj","result",Validity.CURRENT),Observation.absent(Status.UNAVAILABLE,"OFFLINE"))));
        assertThrows(IllegalStateException.class,f::tick);
        assertTrue(f.found().isEmpty());assertEquals("INVENTORY",f.row().getPhase());assertEquals(0,f.row().getVersion());
    }

    @Test void changeGapDoesNotAdvanceOrRecaptureTheBoundary() {
        f.committed = 9;f.tick();
        assertThrows(IllegalStateException.class,f::tick);
        assertEquals(7,f.row().getProcessedSequence());assertEquals(9,f.row().getThroughSequence());
        verify(f.journal,times(1)).capture(any(),any(),any());
        f.changes.add(f.change(8,f.result("a","r1",Validity.CURRENT),true));
        f.changes.add(f.change(9,f.result("b","r2",Validity.CURRENT),true));
        f.tick();assertEquals("LIVE",f.row().getPhase());assertEquals(2,f.found().size());
    }

    @Test void repeatedWakeupsDoNotResetInventoryOrGenerateAnEndlessEmptyLoop() {
        f.tick();f.tick();var live = f.row();
        int events = f.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_test_outbox",Integer.class);
        for (int i=0;i<4;i++) f.tick();
        assertEquals(live.getVersion(),f.row().getVersion());assertEquals(events,f.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_test_outbox",Integer.class));
        assertEquals(7,f.row().getBaselineSequence());
    }

    @Test void changesAfterInventoryAreConsumedInSeparateBoundedPages() {
        f.tick();f.tick();f.committed = 209;
        for (long n=8;n<=209;n++)f.changes.add(f.change(n,f.result("o"+n,"r"+n,Validity.CURRENT),true));
        f.tick();assertEquals("CHANGES",f.row().getPhase());assertEquals(209,f.row().getThroughSequence());
        f.tick();assertEquals(107,f.row().getProcessedSequence());
        f.tick();assertEquals(207,f.row().getProcessedSequence());
        f.tick();assertEquals(209,f.row().getProcessedSequence());assertEquals("LIVE",f.row().getPhase());
        assertEquals(202,f.jdbc.queryForObject("SELECT COUNT(*) FROM proj_result_subscription_candidate",Integer.class));
    }

    @ParameterizedTest @ValueSource(strings={"plan","contract","round","retired"})
    void obsoleteRecipientNeverSelectsAnotherRoundOrChangesBusinessState(String changed) {
        var wakeup = ResultSubscriptionWakeup.create(f.row());
        switch(changed) {
            case "plan" -> f.round.setPlanVersionId(11L);
            case "contract" -> f.round.setContractId(31L);
            case "round" -> when(f.rounds.selectCurrentForUpdate(any())).thenReturn(List.of());
            case "retired" -> f.round.setCurrentMarker(null);
            default -> throw new AssertionError(changed);
        }
        f.worker.process(wakeup);assertEquals("RETIRED",f.row().getPhase());assertEquals(7,f.row().getProcessedSequence());
        verifyNoInteractions(f.sources,f.journal);verify(f.projects,never()).updateById(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO.class));
    }

    @Test void completedNodeWithItsOldPlanRemainsObservableWithoutReopeningHistory() {
        f.project.setActivePlanVersionId(11L);f.plan.setStatus("SUPERSEDED");f.round.setEndedAt(LocalDateTime.now());f.round.setStatus("COMPLETED");
        f.tick();assertEquals("CHANGES",f.row().getPhase());assertEquals("COMPLETED",f.round.getStatus());
    }

    @Test void immutableConfigurationOrTenantMismatchRejectsBeforeOwnerAccess() {
        f.jdbc.update("UPDATE proj_result_subscription SET configuration=REPLACE(configuration,'EXACT_ONE','ANY_MATCHING')");
        assertThrows(IllegalStateException.class,f::tick);verifyNoInteractions(f.sources,f.journal);
        var wakeup = ResultSubscriptionWakeup.create(f.row());TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,() -> f.worker.process(wakeup));
    }

    @Test void missingNativeResultInvalidatesOnlyItsPreviouslyObservedIdentity() {
        var a=f.result("obj","ra",Validity.CURRENT);var b=f.result("other","rb",Validity.CURRENT);
        when(f.sources.inventory(any())).thenReturn(new InventoryPage("b",true,List.of(a,b)));
        f.tick();f.tick();f.committed=8;
        var removed=f.change(8,Observation.absent(Status.NOT_FOUND,"DELETED"),false);f.changes.add(removed);
        when(f.sources.changeQuery(ResultSubscriptionRecoveryFixture.TYPE,removed.source()))
                .thenReturn(new Query(1L,3L,ResultSubscriptionRecoveryFixture.TYPE,"obj",null));
        f.tick();f.tick();
        assertEquals(Status.NOT_FOUND,JsonUtils.parseObject(f.found().getFirst().getObservation(),Observation.class).status());
        assertEquals(Status.AVAILABLE,JsonUtils.parseObject(f.found().getLast().getObservation(),Observation.class).status());
    }

    @Test void pinnedResultLookupDoesNotRequireOrFallBackToInventory() {
        var json = (tools.jackson.databind.node.ObjectNode) f.snapshot.getStages().getFirst().getExecution().get("subscriptions").get(0).get("policy");
        json.put("acquisition","PINNED_RESULT");json.put("pinnedResultId","old-result");
        f.plan.setExecutionSnapshot(JsonUtils.toJsonString(f.snapshot));
        f.jdbc.update("UPDATE proj_result_subscription SET configuration=?",f.snapshot.getStages().getFirst().getExecution().get("subscriptions").get(0).toString());
        when(f.sources.inspect(any())).thenReturn(f.result("object","old-result",Validity.CURRENT));
        f.tick();assertEquals("old-result",f.found().getFirst().getResultId());verify(f.sources,never()).inventory(any());
        verify(f.sources).inspect(new Query(1L,3L,ResultSubscriptionRecoveryFixture.TYPE,null,"old-result"));
    }

    @Test void sourceUnavailableDoesNotCreateAFalseEmptySuccessfulCheckpoint() {
        when(f.sources.inventory(any())).thenThrow(new IllegalStateException("CURSOR_EXPIRED"));
        assertThrows(IllegalStateException.class,f::tick);assertEquals("INVENTORY",f.row().getPhase());assertEquals(7,f.row().getProcessedSequence());
    }

    @ParameterizedTest @ValueSource(strings={"schema","id","tenant","type","fractional-id","string-id","missing-id"})
    void untrustedEnvelopeCannotReachAnyWorker(String corruption) {
        var wakeup=ResultSubscriptionWakeup.create(f.row());var json=(tools.jackson.databind.node.ObjectNode)JsonUtils.parseTree(JsonUtils.toJsonString(wakeup));
        String eventType=ResultSubscriptionWakeup.EVENT_TYPE;
        switch(corruption) {
            case "schema" -> json.put("eventVersion","1");
            case "id" -> json.put("eventId",UUID.randomUUID().toString());
            case "tenant" -> json.put("tenantId",2);
            case "type" -> eventType="unsupported";
            case "fractional-id" -> json.put("executionId",20.5);
            case "string-id" -> json.put("executionId","20");
            case "missing-id" -> json.remove("executionId");
            default -> throw new AssertionError(corruption);
        }
        var message=new PlatformOutboxMessageDTO(wakeup.eventId(),eventType,json.toString(),0,1L,LocalDateTime.now());
        assertThrows(IllegalArgumentException.class,() -> f.delivery.deliver(message));
        verifyNoInteractions(f.projects,f.plans,f.rounds,f.sources,f.journal);
    }
}
