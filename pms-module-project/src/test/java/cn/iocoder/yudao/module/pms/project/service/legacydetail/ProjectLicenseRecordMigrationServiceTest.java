package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectLicenseRecordMigrationServiceTest {
    final ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    final DppmsLicenseRecordApi owner=mock(DppmsLicenseRecordApi.class);
    final DppmsCommerceEntityResolveApi commerce=mock(DppmsCommerceEntityResolveApi.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    final ProjectLegacyDetailMapper mapper=mock(ProjectLegacyDetailMapper.class);
    final ProjectLicenseRecordMigrationService service=new ProjectLicenseRecordMigrationService(sources,evidence,owner,commerce,commands,access,mapper);
    final ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    final Envelope envelope=new Envelope("201","SYNTHETIC-CIPHER","TEST-V1","a".repeat(64));
    final ProjectLicenseRecordMigrationService.Request request=new ProjectLicenseRecordMigrationService.Request(
        new ProjectLegacyEntityMigrationService.Request(2107915417328611330L,2108243439713705986L,"b".repeat(64)),List.of(envelope));
    final ProjectLegacySourceDO source=new ProjectLegacySourceDO();
    final ProjectLegacySnapshotDO snapshot=new ProjectLegacySnapshotDO();
    final ProjectLegacyRecordDO record=new ProjectLegacyRecordDO();
    Map<String,String> fields;
    boolean imported;
    @BeforeEach void setup() {
        source.setId(request.source().sourceId());source.setTenantId(1L);source.setProjectId(100L);source.setSourceProjectKey("101");
        source.setCurrentSnapshotId(request.source().snapshotId());
        snapshot.setId(request.source().snapshotId());snapshot.setSourceReadAt(LocalDateTime.of(2020,1,2,3,4));
        fields=new LinkedHashMap<>();fields.put("id","201");fields.put("sn",null);fields.put("specModel","  MODEL  ");
        fields.put("contract","CONTRACT");fields.put("contractType","ORIGINAL");fields.put("item","PRODUCT");fields.put("status","原状态");
        record.setSourceKey("201");record.setSourceTable("pm_project_license_info_from_license");record.setPayloadJson(cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer.sourcePayloadJson(fields));
        when(sources.loadCorrectionSource(eq(100L),any(),eq(actor))).thenReturn(
            new ProjectLegacyEntityMigrationService.Loaded(source,snapshot,Map.of("licenses",List.of(record))));
        when(commerce.resolveLicenseContracts(any())).thenReturn(List.of(new DppmsCommerceEntityResolveApi.LicenseContract("CONTRACT",999L)));
        when(owner.preview(any())).thenAnswer(inv->List.of(new Preview("201",imported?55L:null,imported?"UNCHANGED":"CREATE",List.of("DEVICE_NOT_MATCHED"))));
        when(owner.apply(any())).thenAnswer(inv->{imported=true;return List.of(new Target("201",55L));});
        when(commands.execute(any(),anyString(),eq(ProjectLicenseRecordMigrationService.Report.class),any(),any())).thenAnswer(inv->
            new ExecutionResult<>(Decision.NEW,inv.getArgument(3,Supplier.class).get()));
    }
    @Test void sourceBusinessColumnsUseOwnerFieldsAndOnlyVerifiedContractIds() {
        var result=service.migrate(100L,request,actor);assertEquals(1,result.report().count());
        var capture=org.mockito.ArgumentCaptor.forClass(Command.class);verify(owner).apply(capture.capture());
        var row=capture.getValue().licenses().getFirst();assertEquals("  MODEL  ",row.productModel());assertEquals("PRODUCT",row.productCode());
        assertNull(row.deviceSn());assertEquals(999L,row.contractId());assertEquals("原状态",row.licenseStatus());
        verify(evidence).reconcile(eq(source),eq(snapshot),eq("licenses"),eq(List.of(record)),anyMap(),eq("LICENSE_RECORD_V1"));
    }
    @Test void missingContractRemainsNullInsteadOfGeneratingAnIdentity() {
        when(commerce.resolveLicenseContracts(any())).thenReturn(List.of(new DppmsCommerceEntityResolveApi.LicenseContract("CONTRACT",null)));
        service.migrate(100L,request,actor);verify(owner).apply(argThat(c->c.licenses().getFirst().contractId()==null));
    }
    @Test void unknownSourceKeyDuplicateEnvelopeOrPlaintextPayloadBlocksBeforeOwner() {
        var unknown=new ProjectLicenseRecordMigrationService.Request(request.source(),List.of(new Envelope("202",envelope.ciphertext(),envelope.keyId(),envelope.codeMac())));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,unknown,actor));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,new ProjectLicenseRecordMigrationService.Request(request.source(),List.of(envelope,envelope)),actor));
        fields.put("licenseCode","SYNTHETIC-PLAINTEXT");record.setPayloadJson(JsonUtils.toJsonString(fields));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(owner);
    }
    @Test void missingOwnerCoverageDoesNotProduceASuccessfulBinding() {
        when(owner.apply(any())).thenReturn(List.of());assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
        verify(evidence,never()).reconcile(any(),any(),anyString(),anyList(),anyMap(),anyString());
    }
    @Test void conflictAndReplayWithChangedFormalTargetAreBlocked() {
        when(commands.execute(any(),anyString(),eq(ProjectLicenseRecordMigrationService.Report.class),any(),any()))
            .thenReturn(new ExecutionResult<>(Decision.CONFLICT,null));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
        var report=new ProjectLicenseRecordMigrationService.Report(request.source().sourceId(),request.source().snapshotId(),1,List.of(new Target("201",56L)),List.of());
        imported=true;
        when(commands.execute(any(),anyString(),eq(ProjectLicenseRecordMigrationService.Report.class),any(),any()))
            .thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,report));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
    }
    @Test void actualLongIdsKeepCommandKeysWithinPlatformLengthAndUseStableSemantics() {
        service.migrate(100L,request,actor);
        verify(commands).execute(argThat(scope->scope.key().length()<=128 && scope.actorId()==0L),argThat(digest->digest.length()==64),
            eq(ProjectLicenseRecordMigrationService.Report.class),any(),any());
    }
    @Test void aNewerUnimportedCaptureDoesNotHideAnAlreadyCarriedFormalLicense() {
        Long oldSnapshot=request.source().snapshotId();source.setCurrentSnapshotId(oldSnapshot+1);
        record.setId(501L);record.setSnapshotId(oldSnapshot);
        var newer=new ProjectLegacyRecordDO();newer.setId(502L);newer.setSnapshotId(oldSnapshot+1);newer.setSourceKey("201");
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));
        when(mapper.selectSourceLicenseRecords(any())).thenReturn(List.of(record,newer));
        when(evidence.statuses(1L,"101",oldSnapshot,List.of(record))).thenReturn(Map.of("201",
            new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(
                new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("AST","DeviceLicenseRecord","ast_device_license_record",55L,"LICENSE_HISTORY",0)))));
        when(evidence.statuses(1L,"101",oldSnapshot+1,List.of(newer))).thenReturn(Map.of("201",
            new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(owner.page(any())).thenAnswer(inv->{
            var query=inv.getArgument(0,Query.class);
            return query.recordIds().equals(List.of(55L))?new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(new Row(55L,Map.of("licenseCodeState","已加密保存"))),1L):cn.iocoder.yudao.framework.common.pojo.PageResult.empty();
        });
        assertEquals(1,service.page(100L,1,20,actor).getTotal());
    }
}
