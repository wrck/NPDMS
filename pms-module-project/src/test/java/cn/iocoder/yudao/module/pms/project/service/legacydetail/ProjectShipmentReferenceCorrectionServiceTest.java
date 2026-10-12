package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectShipmentReferenceCorrectionServiceTest {
    final ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    final DppmsCommerceEntityResolveApi commerce=mock(DppmsCommerceEntityResolveApi.class);
    final DppmsShipmentReferenceCorrectionApi owner=mock(DppmsShipmentReferenceCorrectionApi.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final ProjectShipmentReferenceCorrectionService service=new ProjectShipmentReferenceCorrectionService(sources,evidence,commerce,owner,commands);
    final ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    final ProjectLegacyEntityMigrationService.Request source=new ProjectLegacyEntityMigrationService.Request(2108237852892393473L,2107915330837868545L,"checksum");
    final ProjectShipmentReferenceCorrectionService.PreviewRequest request=new ProjectShipmentReferenceCorrectionService.PreviewRequest(source,List.of("30"));
    final Reference reference=new Reference("30",80L,70L,90L,"SN","ORDER","10625024");
    ProjectLegacyRecordDO shipment,line;
    @BeforeEach void setup() {
        var root=new ProjectLegacySourceDO();root.setId(source.sourceId());root.setSourceProjectKey("OLD-PROJECT");root.setSourceContractNo("CONTRACT");
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(source.snapshotId());
        shipment=record("30","shipment-facts","{\"barcode\":\"SN\",\"orderNumber\":\"ORDER\",\"lineNum\":\"10625024\"}");
        line=record("40","order-lines","{\"source\":\"D365\",\"compCode\":\"01\",\"orderNumber\":\"ORDER\",\"lineNum\":\"1\",\"lineType\":\"0\",\"customInfo\":\"{\\\"inventTransId\\\":\\\"10625024\\\"}\"}");
        var order=record("50","orders","{\"source\":\"D365\",\"compCode\":\"01\",\"orderNumber\":\"ORDER\",\"orderType\":\"0\"}");
        var loaded=new ProjectLegacyEntityMigrationService.Loaded(root,snapshot,Map.of("shipment-facts",List.of(shipment),"order-lines",List.of(line),"orders",List.of(order)));
        when(sources.loadCorrectionSource(100L,source,actor)).thenReturn(loaded);
        when(evidence.statuses(any(),any(),any(),anyList())).thenAnswer(call->{
            List<ProjectLegacyRecordDO> rows=call.getArgument(3);
            return rows.getFirst().getDomainCode().equals("shipment-facts")?Map.of("30",status(
                    new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",80L,"SHIPMENT",0),
                    new ExternalTargetMapping("AST","Device","ast_device",70L,"DEVICE",0))):Map.of("40",status(
                    new ExternalTargetMapping("COM","SalesOrderLine","com_sales_order_line",90L,"BUSINESS_ENTITY",0)));
        });
        when(commerce.lockShipmentLines(any(),anyList())).thenAnswer(call->call.getArgument(1));
        when(owner.preview(any())).thenReturn(List.of(new Baseline(reference,"{}","{}")));
    }
    @Test void previewDerivesIdsFromSuccessfulBindingsAndLocksTheRealInventoryTransaction() {
        assertEquals(reference,service.preview(100L,request,actor).getFirst().reference());
        verify(commerce).lockShipmentLines(any(),argThat(lines->lines.size()==1 && lines.getFirst().id()==90L
                && lines.getFirst().companyCode().equals("01") && lines.getFirst().inventoryTransactionId().equals("10625024")));
        verify(owner).preview(argThat(c->c.references().equals(List.of(reference))));
    }
    @Test void alteredRawInventoryTransactionCannotFallBackToOrderNumberOrProductName() {
        line.setPayloadJson(line.getPayloadJson().replace("10625024","10625025"));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(commerce,owner,commands);
    }
    @Test void absentSuccessfulReceiptBlocksBeforeBothOwners() {
        doReturn(Map.of()).when(evidence).statuses(any(),any(),any(),anyList());
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(commerce,owner,commands);
    }
    @Test void unknownAndDuplicateSourceKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,
                new ProjectShipmentReferenceCorrectionService.PreviewRequest(source,List.of("31")),actor));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,
                new ProjectShipmentReferenceCorrectionService.PreviewRequest(source,List.of("30","30")),actor));verifyNoInteractions(commerce,owner,commands);
    }
    @Test void clientCannotReplaceEitherBoundTargetIdInTheBaseline() {
        var baseline=new Baseline(new Reference("30",81L,70L,90L,"SN","ORDER","10625024"),"{}","{}");
        assertThrows(IllegalArgumentException.class,()->service.correct(100L,
                new ProjectShipmentReferenceCorrectionService.CorrectionRequest(request,List.of(baseline)),actor));verifyNoInteractions(owner,commands);
    }
    @Test void deniedSourceScopeOrAChangedChecksumCannotReachOwners() {
        when(sources.loadCorrectionSource(100L,source,actor)).thenThrow(new IllegalArgumentException("SCOPE_OR_CHECKSUM"));
        assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));verifyNoInteractions(evidence,commerce,owner,commands);
    }
    @Test void replayRechecksAppliedTargetsWithoutRepeatingTheCorrection() {
        doReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,null)).when(commands).execute(any(),anyString(),any(),any(),any());
        var input=new ProjectShipmentReferenceCorrectionService.CorrectionRequest(request,List.of(new Baseline(reference,"{}","{}")));
        assertEquals("REPLAY_COMPLETED",service.correct(100L,input,actor).decision());
        verify(owner).verify(any(),eq(input.baselines()));verify(owner,never()).correct(any(),any());
    }
    @Test void correctionAndItsReceiptShareThePlatformCommandTransaction() {
        doAnswer(call->{
            var scope=call.getArgument(0,IdempotencyScope.class);
            assertTrue(scope.key().length()<=128,"Must fit the platform contract with real 19-digit identities");
            assertTrue(scope.key().startsWith("DPPMS-SHIPMENT-REFERENCES:"));
            return new ExecutionResult<>(Decision.NEW,call.getArgument(3,Supplier.class).get());
        })
                .when(commands).execute(any(),anyString(),eq(ProjectShipmentReferenceCorrectionService.Report.class),any(),any());
        var input=new ProjectShipmentReferenceCorrectionService.CorrectionRequest(request,List.of(new Baseline(reference,"{}","{}")));
        assertEquals(1,service.correct(100L,input,actor).report().sourceCount());verify(owner).correct(any(),eq(input.baselines()));
    }
    private static ProjectLegacyRecordDO record(String key,String domain,String payload) {
        var row=new ProjectLegacyRecordDO();row.setSourceKey(key);row.setDomainCode(domain);row.setPayloadJson(payload);return row;
    }
    private static ProjectLegacyNativeEvidenceService.RowStatus status(ExternalTargetMapping... targets) {
        return new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(targets));
    }
}
