package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectFactoryRecordReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    DppmsAssetSourceHistoryApi assets=mock(DppmsAssetSourceHistoryApi.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectFactoryRecordImportService importer=new ProjectFactoryRecordImportService(evidence);
    ProjectFactoryRecordReconciliationService service=new ProjectFactoryRecordReconciliationService(sources,importer,assets,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    ProjectLegacyEntityMigrationService.Request request=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    ProjectLegacyEntityMigrationService.Loaded loaded;
    List<ProjectLegacyRecordDO> rows=new ArrayList<>();
    ExternalTargetMapping target(String table,long id) { return new ExternalTargetMapping("AST","DeviceFactoryVersionRecord",table,id,"FACTORY_VERSION_SOURCE",0); }
    void prepare(boolean existing) {
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setTenantId(1L);source.setSourceProjectKey("101");
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setChecksum("a".repeat(64));snapshot.setSourceReadAt(LocalDateTime.of(2024,1,2,3,4));
        rows.clear();Map<String,ProjectLegacyNativeEvidenceService.RowStatus> statuses=new HashMap<>();
        for(int i=1;i<=2;i++) {
            var row=new ProjectLegacyRecordDO();row.setTenantId(1L);row.setSnapshotId(20L);row.setDomainCode("factory-versions");
            row.setSourceTable("fb_soft_version");row.setSourceKey("same-content:"+i);
            row.setPayloadJson("{\"serial_number\":\"SN\",\"conp\":\" V \",\"boot\":\"\",\"cpld\":null,\"pcb\":\"P\"}");rows.add(row);
            statuses.put(row.getSourceKey(),new ProjectLegacyNativeEvidenceService.RowStatus(existing?"MAPPED":"NOT_MIGRATED",null,null,
                    existing?List.of(target("ast_device_factory_version_record",700L+i)):List.of()));
        }
        loaded=new ProjectLegacyEntityMigrationService.Loaded(source,snapshot,Map.of("factory-versions",rows));
        doReturn(loaded).when(sources).loadCorrectionSource(100L,request,actor);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(statuses);
        doAnswer(call->{
            var input=call.getArgument(0,DppmsAssetSourceHistoryApi.Command.class);var output=new ArrayList<NativeEntityMigrationRow>();
            for(int i=0;i<input.factories().size();i++) {
                var f=input.factories().get(i);assertEquals(" V ",f.conp());assertEquals("",f.boot());assertNull(f.cpld());
                output.add(NativeEntityMigrationRow.mapped("factory-versions",f.sourceKey(),target("ast_device_factory_version_record",f.mappedRecordId()==null?701L+i:f.mappedRecordId())));
            }
            return output;
        }).when(assets).importSourceHistory(any());
        doAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,call.getArgument(3,Supplier.class).get()))
                .when(commands).execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any());
    }
    @Test void identicalOccurrencesKeepIndependentBindingsAndNeverTouchTheUniqueMaster() {
        prepare(true);var command=importer.command(100L,loaded,actor);
        assertEquals(List.of(701L,702L),command.factories().stream().map(DppmsAssetSourceHistoryApi.FactoryVersion::mappedRecordId).toList());
        var result=service.reconcile(100L,request,actor);assertEquals(2,result.report().sourceCount());
        verify(evidence).reconcile(any(),any(),eq("factory-versions"),eq(rows),anyMap(),eq("FACTORY_RECORD_V1"));
    }
    @Test void newRecordsUseTheTransactionalCommandBeforeAnyInsertAndPersistEveryOccurrence() {
        prepare(false);assertEquals(2,service.reconcile(100L,request,actor).report().sourceCount());
        var order=inOrder(commands,assets);order.verify(commands).execute(any(),anyString(),any(),any(),any());order.verify(assets).importSourceHistory(any());
    }
    @Test void sourceColumnOmissionAndAmbiguousBindingBlockBeforeOwnerWrites() {
        prepare(true);rows.getFirst().setPayloadJson(rows.getFirst().getPayloadJson().replace(",\"cpld\":null",""));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(assets,commands);
        prepare(true);when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of(rows.getFirst().getSourceKey(),
                new ProjectLegacyNativeEvidenceService.RowStatus("AMBIGUOUS",null,null,List.of())));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(assets,commands);
    }
    @Test void cachedSuccessStillRevalidatesCurrentMappedRecordsAndDeniedSourceHasNoOwnerAccess() {
        when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("DENIED"));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(evidence,assets,commands);
        prepare(true);doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,null))
                .when(commands).execute(any(),anyString(),any(),any(),any());
        assertEquals("REPLAY_COMPLETED",service.reconcile(100L,request,actor).decision());
        doThrow(new IllegalArgumentException("TARGET_CHANGED")).when(assets).importSourceHistory(any());
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verify(commands,times(1)).execute(any(),anyString(),any(),any(),any());
    }
    @Test void wrongOwnerTargetCannotProduceSuccessEvidence() {
        prepare(false);doReturn(List.of(
                NativeEntityMigrationRow.mapped("factory-versions",rows.getFirst().getSourceKey(),target("ast_device_factory_version",701)),
                NativeEntityMigrationRow.mapped("factory-versions",rows.getLast().getSourceKey(),target("ast_device_factory_version_record",702)))).when(assets).importSourceHistory(any());
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));
        verify(evidence,never()).reconcile(any(),any(),any(),anyList(),anyMap(),any());
    }
}
