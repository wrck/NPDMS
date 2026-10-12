package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProjectLeaseProductReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    ProjectLeaseProductImportService importer=mock(ProjectLeaseProductImportService.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectLeaseProductReconciliationService service=new ProjectLeaseProductReconciliationService(sources,importer,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    ProjectLegacyEntityMigrationService.Request request=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    ProjectLeaseProductImportService.Plan plan=new ProjectLeaseProductImportService.Plan(100L,List.of());
    @BeforeEach void setup() {
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setTenantId(1L);source.setSourceProjectKey("101");
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);
        when(sources.loadCorrectionSource(100L,request,actor)).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(source,snapshot,
                Map.of("lease-products",List.of(),"project",List.of())));
        when(importer.prepare(any(),any(),anyList(),anyList(),any())).thenReturn(plan);
        when(importer.apply(plan,actor)).thenReturn(new ProjectLeaseProductImportService.Result(List.of(),Map.of()));
        doAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,call.getArgument(3,Supplier.class).get()))
                .when(commands).execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any());
    }
    @Test void previewChecksSourceAuthorizationAndOwnerWithoutWritingOrStartingCommand() {
        assertTrue(service.preview(100L,request,actor).isEmpty());verify(importer).prepare(100L,"101",List.of(),List.of(),actor);
        verify(importer,never()).apply(any(),any());verifyNoInteractions(commands,evidence);
    }
    @Test void emptyPilotStillProducesItsOwnReusableReceiptWithoutBusinessRecords() {
        var result=service.reconcile(100L,request,actor);assertEquals("NEW",result.decision());assertEquals(0,result.report().sourceCount());
        var order=inOrder(importer,commands);order.verify(importer).prepare(any(),any(),anyList(),anyList(),any());
        order.verify(commands).execute(any(),anyString(),any(),any(),any());order.verify(importer).apply(plan,actor);
        verify(evidence).reconcileAssociatedList(any(),any(),eq("lease-products"),eq(List.of()),eq(Map.of()),eq(Map.of()));
    }
    @Test void replayAlwaysRechecksBusinessBaselineAndNeverReinserts() {
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                new ProjectLegacyEntityMigrationService.CorrectionReport(10L,20L,"lease-products",0,List.of())))
                .when(commands).execute(any(),anyString(),any(),any(),any());
        assertEquals("REPLAY_COMPLETED",service.reconcile(100L,request,actor).decision());
        verify(importer).prepare(any(),any(),anyList(),anyList(),any());verify(importer,never()).apply(any(),any());verifyNoInteractions(evidence);
        doThrow(new IllegalArgumentException("TARGET_CHANGED")).when(importer).prepare(any(),any(),anyList(),anyList(),any());
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verify(commands,times(1)).execute(any(),anyString(),any(),any(),any());
    }
    @Test void sourceHeadChecksumOrProjectAuthorizationFailureStopsBeforeOwnerAccess() {
        when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("SOURCE_CHANGED_OR_DENIED"));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(importer,evidence,commands);
    }
}
