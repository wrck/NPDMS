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
class ProjectExecutionFieldReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    DppmsCommerceDetailImportApi commerce=mock(DppmsCommerceDetailImportApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectExecutionFieldReconciliationService service=new ProjectExecutionFieldReconciliationService(sources,commerce,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    ProjectLegacyEntityMigrationService.Request request=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    ProjectLegacyRecordDO original;
    void prepare() {
        var source=new ProjectLegacySourceDO();source.setSourceContractNo("C");var contract=new ProjectLegacyRecordDO();
        contract.setSourceKey("1");contract.setPayloadJson("{\"contractNo\":\"C\"}");
        original=new ProjectLegacyRecordDO();original.setDomainCode("executions");original.setSourceKey("2");
        Map<String,String> raw=new LinkedHashMap<>();ProjectExecutionFieldReconciliationService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id","2","orderExecNumber","E","dataSource","D365","corporationCode","01","channelName","","customerProjectName","",
                "submitTime","2023-02-24 11:19:34.321","engineeFee","1,234.50"));
        var json=new StringJoiner(",","{","}");raw.forEach((k,v)->json.add(JsonUtils.toJsonString(k)+":"+(v==null?"null":JsonUtils.toJsonString(v))));
        original.setPayloadJson(json.toString());
        doReturn(new ProjectLegacyEntityMigrationService.Loaded(source,new ProjectLegacySnapshotDO(),
                Map.of("contracts",List.of(contract),"executions",List.of(original)))).when(sources).loadCorrectionSource(100L,request,actor);
        when(commerce.importDetails(any())).thenReturn(List.of(NativeEntityMigrationRow.mapped("executions","2",
                new ExternalTargetMapping("COM","CrmExecutionOrder","com_crm_execution_order",30L,"BUSINESS_ENTITY",0))));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,call.getArgument(3,Supplier.class).get()));
    }
    @Test void fullOriginalExecutionUsesOnlyItsOwnerAndPreservesEmptyValuesAndMilliseconds() {
        prepare();assertEquals(1,service.reconcile(100L,request,actor).report().sourceCount());
        verify(commerce).importDetails(argThat(c->c.orders().isEmpty()&&c.lines().isEmpty()&&c.packages().isEmpty()&&c.plans().isEmpty()
                && "01".equals(c.executions().getFirst().sourceCompanyCode())&& "".equals(c.executions().getFirst().channelName())
                && "".equals(c.executions().getFirst().customerProjectName())&&c.executions().getFirst().submitTime().getNano()==321000000
                &&"1,234.50".equals(c.executions().getFirst().engineeringFeeRaw())));
        verify(evidence).reconcile(any(),any(),eq("executions"),anyList(),anyMap(),eq("EXECUTION_FIELDS_V1"));
    }
    @Test void missingOriginalColumnAndRetainedOwnerResultCannotCreateSuccessEvidence() {
        prepare();original.setPayloadJson(original.getPayloadJson().replace("\"agentName\":null,",""));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commerce,commands,evidence);
        prepare();when(commerce.importDetails(any())).thenReturn(List.of(NativeEntityMigrationRow.retained("executions","2","ORGANIZATION_CONFLICT")));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commands,evidence);
    }
    @Test void wrongTargetCannotProduceSuccessfulReceipt() {
        prepare();when(commerce.importDetails(any())).thenReturn(List.of(NativeEntityMigrationRow.mapped("executions","2",
                new ExternalTargetMapping("COM","SalesOrder","com_sales_order",30L,"BUSINESS_ENTITY",0))));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commands,evidence);
    }
    @Test void deniedSourceStopsOwnerAndCompletedReceiptDoesNotSkipCurrentOwnerValidation() {
        when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("DENIED"));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commerce,commands,evidence);
        prepare();when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,null));
        service.reconcile(100L,request,actor);when(commerce.importDetails(any())).thenThrow(new IllegalArgumentException("TARGET_CHANGED"));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verify(commands,times(1)).execute(any(),anyString(),any(),any(),any());
    }
}
