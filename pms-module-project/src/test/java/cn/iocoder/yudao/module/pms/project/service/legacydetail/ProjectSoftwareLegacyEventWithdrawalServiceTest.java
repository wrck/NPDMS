package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectSoftwareLegacyEventWithdrawalServiceTest {
    final ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    final ProjectLegacyDetailMapper legacy=mock(ProjectLegacyDetailMapper.class);
    final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    final DppmsSoftwareLegacyEventWithdrawalApi owner=mock(DppmsSoftwareLegacyEventWithdrawalApi.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final ProjectSoftwareLegacyEventWithdrawalService service=new ProjectSoftwareLegacyEventWithdrawalService(sources,legacy,evidence,owner,commands);
    final ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    final ProjectLegacyEntityMigrationService.Request source=new ProjectLegacyEntityMigrationService.Request(10L,20L,"checksum",99L);
    final ProjectSoftwareLegacyEventWithdrawalService.PreviewRequest request=new ProjectSoftwareLegacyEventWithdrawalService.PreviewRequest(source,19L);
    final Identity identity=new Identity(30L,80L,70L);
    ProjectLegacySnapshotDO old;
    ProjectLegacyEntityMigrationService.Loaded loaded;
    @BeforeEach void setup() {
        var root=new ProjectLegacySourceDO();root.setId(10L);root.setTenantId(1L);root.setSourceProjectKey("100");
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setChecksum("checksum");snapshot.setSourceReadAt(LocalDateTime.of(2026,1,1,0,0));
        loaded=new ProjectLegacyEntityMigrationService.Loaded(root,snapshot,Map.of());
        when(sources.loadCorrectionSource(100L,source,actor)).thenReturn(loaded);
        var observation=JsonUtils.parseObject("{\"sourceId\":30,\"barCode\":\"SN\",\"mappedRecordId\":70}",DppmsAssetSourceHistoryApi.SoftwareObservation.class);
        when(sources.softwareObservations(loaded,actor,99L)).thenReturn(List.of(observation));when(sources.softwareBatches(loaded,actor)).thenReturn(List.of());
        old=new ProjectLegacySnapshotDO();old.setId(19L);old.setTenantId(1L);old.setSourceId(10L);when(legacy.selectSnapshot(any())).thenReturn(old);
        var row=new ProjectLegacyRecordDO();row.setSourceKey("30");row.setDomainCode("software-versions");
        when(legacy.selectRecords(any())).thenReturn(List.of(row));
        when(evidence.legacySoftwareEventStatuses(any(),any(),any(),anyList())).thenReturn(Map.of("30",new ProjectLegacyNativeEvidenceService.RowStatus(
                "MAPPED",null,null,List.of(new ExternalTargetMapping("AST","DeviceNetworkVersionEvent","ast_device_network_version_event",80L,"SOFTWARE_EVENT",0)))));
        when(owner.preview(any(),anyList())).thenReturn(List.of(new Baseline(identity,"{}")));
    }
    @Test void previewDerivesBothIdsFromFormalAndOldSuccessfulBindings() {
        assertEquals(identity,service.preview(100L,request,actor).getFirst().identity());
        verify(owner).preview(any(),eq(List.of(identity)));
    }
    @Test void anotherSourcesSnapshotCannotAuthorizeAnyOwnerAction() {
        old.setSourceId(11L);assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));
        verifyNoInteractions(evidence,owner,commands);
    }
    @Test void changedReceiptOrWrongOwnerBindingBlocksBeforeTheOwner() {
        when(evidence.legacySoftwareEventStatuses(any(),any(),any(),anyList())).thenReturn(Map.of("30",new ProjectLegacyNativeEvidenceService.RowStatus("CHECKSUM_CONFLICT",null,null,List.of())));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(owner,commands);
    }
    @Test void clientCannotWithdrawAnIdOutsideTheSuccessfulReceipt() {
        var input=new ProjectSoftwareLegacyEventWithdrawalService.WithdrawalRequest(request,List.of(new Baseline(new Identity(30L,81L,70L),"{}")));
        assertThrows(IllegalArgumentException.class,()->service.withdraw(100L,input,actor));verifyNoInteractions(owner,commands);
    }
    @Test void newWithdrawalAndItsReceiptUseTheSameCommandTransaction() {
        doAnswer(call->new ExecutionResult<>(Decision.NEW,call.getArgument(3,Supplier.class).get()))
                .when(commands).execute(any(),anyString(),eq(ProjectSoftwareLegacyEventWithdrawalService.Report.class),any(),any());
        var input=new ProjectSoftwareLegacyEventWithdrawalService.WithdrawalRequest(request,List.of(new Baseline(identity,"{}")));
        assertEquals(1,service.withdraw(100L,input,actor).report().eventCount());
        var order=inOrder(commands,owner);order.verify(commands).execute(any(),anyString(),any(),any(),any());order.verify(owner).withdraw(any());
    }
    @Test void replayRevalidatesFormalHistoryAndAbsenceWithoutDeletingAgain() {
        doReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,null)).when(commands).execute(any(),anyString(),any(),any(),any());
        var input=new ProjectSoftwareLegacyEventWithdrawalService.WithdrawalRequest(request,List.of(new Baseline(identity,"{}")));
        assertEquals("REPLAY_COMPLETED",service.withdraw(100L,input,actor).decision());
        verify(owner).verifyWithdrawn(any(),eq(List.of(identity)));verify(owner,never()).withdraw(any());
    }
}
