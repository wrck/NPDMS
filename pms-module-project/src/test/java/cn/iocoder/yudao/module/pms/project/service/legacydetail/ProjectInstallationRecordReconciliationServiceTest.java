package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectInstallationRecordReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    DppmsInstallationRecordMigrationApi owner=mock(DppmsInstallationRecordMigrationApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectInstallationRecordReconciliationService service=new ProjectInstallationRecordReconciliationService(sources,owner,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    ProjectInstallationRecordReconciliationService.InstallationRequest request=new ProjectInstallationRecordReconciliationService.InstallationRequest(10L,20L,"a".repeat(64),List.of("301"));
    ProjectLegacyRecordDO row;
    DppmsInstallationRecordMigrationApi.Baseline baseline=new DppmsInstallationRecordMigrationApi.Baseline(700L,"{\"updater\":null}",null,null);
    DppmsInstallationRecordMigrationApi.Baseline after=new DppmsInstallationRecordMigrationApi.Baseline(700L,"{\"updater\":null,\"status\":\"2\"}",null,null);
    void prepare() {
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("101");row=new ProjectLegacyRecordDO();
        row.setId(30L);row.setSourceKey("301");row.setTenantId(1L);row.setSnapshotId(20L);row.setDomainCode("installations");row.setSourceTable("pm_project_shipment");
        var values=new LinkedHashMap<String,String>();DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(k->values.put(k,null));
        values.putAll(Map.of("id","301","projectId","101","barcode","SN","installAddress","旧地址","transferFlag","-1","createTime","2020-01-02 03:04:05"));
        row.setPayloadJson(values.entrySet().stream().map(e->JsonUtils.toJsonString(e.getKey())+":"+JsonUtils.toJsonString(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}")));
        when(sources.loadCorrectionSource(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor))
                .thenReturn(new ProjectLegacyEntityMigrationService.Loaded(source,new ProjectLegacySnapshotDO(),Map.of("installations",List.of(row))));
        var target=new ExternalTargetMapping("IMP","Installation","imp_eng_installation",700L,"BUSINESS_ENTITY",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("301",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target),null,baseline)));
        var outcome=List.of(new DppmsInstallationRecordMigrationApi.Result("301",700L,"UPDATED",after));
        when(owner.preview(any())).thenReturn(outcome);when(owner.apply(any())).thenReturn(outcome);
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,call.getArgument(3,Supplier.class).get()));
    }
    @Test void previewCarriesAllRawFieldsWithExplicitNullAndNoWrite() {
        prepare();assertEquals(700L,service.preview(100L,request,actor).getFirst().targetId());
        verify(owner).preview(argThat(c->c.entries().getFirst().originalFields().size()==21 && c.entries().getFirst().originalFields().containsKey("updateBy")
                && c.entries().getFirst().originalFields().get("updateBy")==null && c.entries().getFirst().baseline().equals(baseline)));
        verify(owner,never()).apply(any());verifyNoInteractions(commands);
    }
    @Test void writesOnlyFormalInstallationBindingAndPostCorrectionGuard() {
        prepare();var result=service.reconcile(100L,request,actor);assertEquals("NEW",result.decision());
        assertEquals("imp_eng_installation",result.report().targets().getFirst().targets().getFirst().targetTable());
        verify(evidence).reconcile(any(),any(),eq("installations"),anyList(),anyMap(),eq("INSTALLATION_RECORD_V2"),eq(Map.of()),eq(Map.of("301",after)));
    }
    @Test void absentAuditGuardAndSourceChecksumConflictAreBlocked() {
        prepare();when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("301",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of())));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(owner,commands);
    }
    @Test void sourceDenialAndMissingRawColumnDoNotReachOwner() {
        prepare();row.setPayloadJson(row.getPayloadJson().replace("\"itemModel\":null,",""));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(owner,commands);
        when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("DENIED"));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));
    }
    @Test void ownerCannotReturnDifferentIdOrIncompleteCoverage() {
        prepare();when(owner.apply(any())).thenReturn(List.of(new DppmsInstallationRecordMigrationApi.Result("301",701L,"UPDATED",after)));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));
        verify(evidence,never()).reconcile(any(),any(),anyString(),anyList(),anyMap(),anyString(),anyMap(),anyMap());
        when(owner.apply(any())).thenReturn(List.of());assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));
    }
    @Test void replayRevalidatesTargetWithoutApplyingAgain() {
        prepare();var report=new ProjectLegacyEntityMigrationService.CorrectionReport(10L,20L,"installations",1,List.of());
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,report));
        assertEquals("REPLAY_COMPLETED",service.reconcile(100L,request,actor).decision());verify(owner).preview(any());verify(owner,never()).apply(any());
    }
    @Test void commandKeyFitsPlatformContractForNineteenDigitSourceIds() {
        prepare();var loaded=new ProjectLegacySourceDO();loaded.setSourceProjectKey("101");
        when(sources.loadCorrectionSource(any(),any(),any())).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(loaded,new ProjectLegacySnapshotDO(),Map.of("installations",List.of(row))));
        var large=new ProjectInstallationRecordReconciliationService.InstallationRequest(Long.MAX_VALUE,Long.MAX_VALUE-1,"a".repeat(64),List.of("301"));
        service.reconcile(100L,large,actor);
        verify(commands).execute(argThat(scope->scope.key().length()<=128 && scope.key().startsWith("DPPMS-INSTALLATION-RECORD:")),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any());
    }
    @Test void extraOrDuplicateSelectedKeysAreBlocked() {
        prepare();var other=new ProjectInstallationRecordReconciliationService.InstallationRequest(10L,20L,"a".repeat(64),List.of("301","302"));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,other,actor));verifyNoInteractions(owner,commands);
    }
}
