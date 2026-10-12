package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.ContractCollectionPlanSyncProofApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectContractCollectionPlanReconciliationServiceTest {
    ProjectLegacyEntityMigrationService migration=mock(ProjectLegacyEntityMigrationService.class);
    ContractCollectionPlanSyncProofApi proofs=mock(ContractCollectionPlanSyncProofApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectContractCollectionPlanReconciliationService service=new ProjectContractCollectionPlanReconciliationService(migration,proofs,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    ProjectContractCollectionPlanReconciliationService.Request request=new ProjectContractCollectionPlanReconciliationService.Request(2L,3L,"a".repeat(64),12L);
    Map<String,String> raw;
    ProjectLegacyRecordDO row;
    ProjectLegacySourceDO source;
    ProjectLegacySnapshotDO snapshot;
    @BeforeEach void setup() {
        raw=new LinkedHashMap<>();
        for(String name:List.of("id","contractNo","batchCode","basicDataName","referenceEventName","eventPlanHappenDate","afterDaysNum",
                "eventActualFinishDate","marketingFeedback","createBy","createTime","updateBy","updateTime","effectiveFrom","effectiveTo","dataSource")) raw.put(name,null);
        raw.put("id","10");raw.put("contractNo"," C1 ");raw.put("afterDaysNum","30");raw.put("createTime","2018-01-02 03:04:05.123");
        Map<String,Object> payload=new LinkedHashMap<>();raw.forEach((key,value)->payload.put(key,value==null?tools.jackson.databind.node.NullNode.getInstance():value));
        row=new ProjectLegacyRecordDO().setSourceKey("10").setPayloadJson(JsonUtils.toJsonString(payload));
        source=new ProjectLegacySourceDO();source.setId(2L);snapshot=new ProjectLegacySnapshotDO();snapshot.setId(3L);
        when(migration.loadCorrectionSource(eq(100L),any(),eq(actor))).thenReturn(new Loaded(source,snapshot,Map.of("plans",List.of(row))));
        when(proofs.verify(any())).thenAnswer(call->{ContractCollectionPlanSyncProofApi.Query query=call.getArgument(0);
            assertEquals(12L,query.taskId());assertEquals(1L,query.tenantId());assertEquals(7L,query.actorId());
            return List.of(new ContractCollectionPlanSyncProofApi.Proof("10",40L,raw));});
        when(commands.execute(any(),anyString(),eq(CorrectionReport.class),any(),any())).thenAnswer(call->{
            Supplier<CorrectionReport> operation=call.getArgument(3);return new ExecutionResult<>(Decision.NEW,operation.get());});
    }
    @Test void createsSeparateCorrectionEvidenceAfterSyncProofMatchesAllOriginalColumns() {
        var report=service.reconcile(100L,request,actor).report();
        assertEquals("com_contract_collection_plan",report.targets().getFirst().targets().getFirst().targetTable());
        assertEquals(40L,report.targets().getFirst().targets().getFirst().targetId());
        verify(evidence).reconcile(eq(source),eq(snapshot),eq("plans"),eq(List.of(row)),anyMap(),eq("COLLECTION_PLAN_V1"));
    }
    @Test void unauthorizedSourceStopsBeforeSyncAndEvidence() {
        when(migration.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("DENIED"));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(proofs,evidence,commands);
    }
    @Test void differentSourceRowOrDuplicateTargetCannotCreateReceipt() {
        raw.put("contractNo","different");
        assertThrows(IllegalStateException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(evidence,commands);
        raw.put("contractNo"," C1 ");
        doReturn(List.of(new ContractCollectionPlanSyncProofApi.Proof("10",40L,raw),new ContractCollectionPlanSyncProofApi.Proof("10",40L,raw))).when(proofs).verify(any());
        assertThrows(IllegalStateException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(evidence,commands);
    }
    @Test void dateFormattingIsNormalizedWithoutDroppingMillisecondsOrEmptyValues() {
        var iso=new LinkedHashMap<>(raw);iso.put("createTime","2018-01-02T03:04:05.123");
        assertTrue(ProjectContractCollectionPlanReconciliationService.sameOriginal(raw,iso));
        iso.put("createTime","2018-01-02T03:04:05");assertFalse(ProjectContractCollectionPlanReconciliationService.sameOriginal(raw,iso));
        iso.put("createTime",raw.get("createTime"));iso.put("marketingFeedback","");
        assertFalse(ProjectContractCollectionPlanReconciliationService.sameOriginal(raw,iso));
    }
    @Test void replayStillChecksCurrentSyncProofBeforeReturningCachedReceipt() {
        var first=service.reconcile(100L,request,actor);
        when(commands.execute(any(),anyString(),eq(CorrectionReport.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,first.report()));
        service.reconcile(100L,request,actor);
        verify(evidence,times(1)).reconcile(eq(source),eq(snapshot),eq("plans"),anyList(),anyMap(),eq("COLLECTION_PLAN_V1"));
        doThrow(new IllegalStateException("changed")).when(proofs).verify(any());
        assertThrows(IllegalStateException.class,()->service.reconcile(100L,request,actor));verify(commands,times(2)).execute(any(),anyString(),eq(CorrectionReport.class),any(),any());
    }
}
