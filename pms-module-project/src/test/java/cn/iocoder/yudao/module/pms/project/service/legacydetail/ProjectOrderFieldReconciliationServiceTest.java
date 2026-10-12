package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectOrderFieldReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    DppmsCommerceDetailImportApi commerce=mock(DppmsCommerceDetailImportApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectOrderFieldReconciliationService service=new ProjectOrderFieldReconciliationService(sources,commerce,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    ProjectLegacyEntityMigrationService.Request request=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    ProjectLegacyRecordDO row(String domain,String key,Map<String,String> values) {
        var r=new ProjectLegacyRecordDO();r.setDomainCode(domain);r.setSourceKey(key);r.setPayloadJson(JsonUtils.toJsonString(values));return r;
    }
    void prepare() {
        var source=new ProjectLegacySourceDO();source.setSourceContractNo("C");
        var order=row("orders","21",Map.of("source","SAP","compCode","01","orderType","0","orderNumber","O",
                "orderCreateTime","2023-02-24 11:19:34.321","syncTime","2024-01-02 00:00:00.123"));
        var line=row("order-lines","22",Map.of("source","SAP","compCode","01","lineType","0","orderNumber","O","lineNum","1","bundleCode",""));
        when(sources.loadCorrectionSource(100L,request,actor)).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(
                source,new ProjectLegacySnapshotDO(),Map.of("contracts",List.of(row("contracts","23",Map.of("contractNo","C"))),
                    "orders",List.of(order),"order-lines",List.of(line))));
        when(commerce.importDetails(any())).thenReturn(List.of(
                NativeEntityMigrationRow.mapped("orders","21",new ExternalTargetMapping("COM","SalesOrder","com_sales_order",31L,"BUSINESS_ENTITY",0)),
                NativeEntityMigrationRow.mapped("order-lines","22",new ExternalTargetMapping("COM","SalesOrderLine","com_sales_order_line",32L,"BUSINESS_ENTITY",0))));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        call.getArgument(3,Supplier.class).get()));
    }
    @Test void onlyOrderOwnersAreCalledAndOriginalPrecisionAndEmptyValuesSurvive() {
        prepare();var result=service.reconcile(100L,request,actor);
        assertEquals(2,result.report().sourceCount());assertEquals("orders-and-lines",result.report().domain());
        verify(commerce).importDetails(argThat(c->c.packages().isEmpty()&&c.plans().isEmpty()&&c.executions().isEmpty()
                && c.identities().executions().isEmpty()&&c.identities().packages().isEmpty()
                && c.orders().getFirst().orderCreateTime().getNano()==321000000
                && c.orders().getFirst().originalSourceSyncTime().getNano()==123000000
                && "".equals(c.lines().getFirst().bundleCode())));
        verify(evidence).reconcile(any(),any(),eq("orders"),anyList(),anyMap(),eq("ORDER_FIELDS_V1"));
        verify(evidence).reconcile(any(),any(),eq("order-lines"),anyList(),anyMap(),eq("ORDER_FIELDS_V1"));
        verify(commands).execute(argThat(c->c.scopeCode().equals("PROJ:DPPMS:ORDER_FIELDS_CORRECTION")),anyString(),any(),any(),any());
    }
    @Test void deniedOrStaleSourceCannotInvokeCommerce() {
        when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("STALE_OR_DENIED"));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commerce,commands,evidence);
    }
    @Test void MissingRetainedAndWrongTableResultsCannotCreateSuccessfulEvidence() {
        prepare();
        for(var outcomes:List.of(List.<NativeEntityMigrationRow>of(),
                List.of(NativeEntityMigrationRow.retained("orders","21","TARGET_CHANGED")),
                List.of(NativeEntityMigrationRow.mapped("orders","21",new ExternalTargetMapping("COM","Shipment","com_shipment_package",31L,"BUSINESS_ENTITY",0))))) {
            when(commerce.importDetails(any())).thenReturn(outcomes);
            assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));
        }
        verifyNoInteractions(commands,evidence);
    }
    @Test void completedReceiptDoesNotSkipOwnerValidationOnReplay() {
        prepare();
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,null));
        service.reconcile(100L,request,actor);
        when(commerce.importDetails(any())).thenThrow(new IllegalStateException("TARGET_CHANGED"));
        assertThrows(IllegalStateException.class,()->service.reconcile(100L,request,actor));
        verify(commerce,times(2)).importDetails(any());verify(commands,times(1)).execute(any(),anyString(),any(),any(),any());
    }
}
