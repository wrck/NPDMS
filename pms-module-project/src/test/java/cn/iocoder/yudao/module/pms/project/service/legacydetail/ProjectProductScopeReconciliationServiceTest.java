package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ProjectProductScopeReconciliationServiceTest {
    ProjectLegacyEntityMigrationService sources=mock(ProjectLegacyEntityMigrationService.class);
    ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    DppmsProjectProductScopeApi scopes=mock(DppmsProjectProductScopeApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectProductScopeReconciliationService service=new ProjectProductScopeReconciliationService(sources,new ProjectProductScopeImportService(projects,evidence),scopes,evidence,commands);
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    ProjectLegacyEntityMigrationService.Request request=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    DppmsProjectProductScopeApi.Baseline baseline=new DppmsProjectProductScopeApi.Baseline(9001L,"original actor",LocalDateTime.of(2020,1,2,3,4),null,LocalDateTime.of(2020,1,2,3,4));
    ProjectLegacyRecordDO sourceRow;
    void prepare() {
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("101");var row=new ProjectLegacyRecordDO();
        sourceRow=row;row.setId(30L);row.setTenantId(1L);row.setSnapshotId(20L);row.setSourceKey("1");row.setSourceTable("pm_project_product_line");row.setDomainCode("products");
        Map<String,String> raw=new LinkedHashMap<>();DppmsProjectProductScopeApi.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id","1","projectId","101","contractNo","C","itemCode","I","itemName","Historical product","orderQuantity","4","deliverQuantity","4","openQuantity","0","orderNumber","O","lineNum","1"));
        // Framework JSON omits null map entries; the immutable original-table payload explicitly keeps them.
        row.setPayloadJson(JsonUtils.toJsonString(raw).replaceFirst("\\{","{\"projectQuantity\":null,"));when(sources.loadCorrectionSource(100L,request,actor)).thenReturn(
                new ProjectLegacyEntityMigrationService.Loaded(source,new ProjectLegacySnapshotDO(),Map.of("products",List.of(row))));
        var project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setVersion(3L);when(projects.selectById(100L)).thenReturn(project);
        var target=new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("COM","DeliveryScope","com_delivery_scope",9001L,"BUSINESS_ENTITY",0);
        when(evidence.statuses(1L,"101",20L,List.of(row))).thenReturn(Map.of("1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target),baseline)));
        var results=List.of(new DppmsProjectProductScopeApi.Result("1",9001L,"CREATED",Map.of()));
        when(scopes.preview(any())).thenReturn(results);when(scopes.apply(any())).thenReturn(results);
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,call.getArgument(3,Supplier.class).get()));
    }
    @Test void previewUsesAllElevenRawFieldsAndPreservesExistingIdAndAuditWithoutWrites() {
        prepare();assertEquals(9001L,service.preview(100L,request,actor).getFirst().targetId());
        verify(scopes).preview(argThat(c->c.entries().getFirst().originalFields().size()==11
                && c.entries().getFirst().originalFields().containsKey("projectQuantity")
                && c.entries().getFirst().originalFields().get("projectQuantity")==null
                && c.entries().getFirst().baseline().id()==9001L && c.entries().getFirst().baseline().createTime().equals(baseline.createTime())));
        verifyNoInteractions(commands);verify(scopes,never()).apply(any());
        verify(evidence,never()).reconcile(any(),any(),anyString(),anyList(),anyMap(),anyString(),anyMap());
    }
    @Test void promotionRecordsScopeReceiptWithImmutableAudit() {
        prepare();var result=service.reconcile(100L,request,actor);assertEquals("products",result.report().domain());
        assertEquals("com_delivery_scope",result.report().targets().getFirst().targets().getFirst().targetTable());
        verify(evidence).reconcile(any(),any(),eq("products"),anyList(),anyMap(),eq("PRODUCT_SCOPE_V4"),eq(Map.of("1",baseline)));
    }
    @Test void companionRepairAddsANewReceiptWithOwnerDetailAndKeepsParentBinding() {
        prepare();var detail=Map.<String,Object>of("id",9101L,"deliveryScopeId",9001L,"allocatedQty",4);
        var repaired=List.of(new DppmsProjectProductScopeApi.Result("1",9001L,"UPDATED",
                Map.of("productDetailAction","CREATED","productDetail",detail)));
        when(scopes.apply(any())).thenReturn(repaired);
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->{
                    PlatformCommandExecutionApi.IdempotencyScope scope=call.getArgument(0);
                    assertEquals("DPPMS-PRODUCT-SCOPE:10:20:V4_PRODUCT_DETAILS",scope.key());
                    var report=call.getArgument(3,Supplier.class).get();
                    var factory=call.getArgument(4,java.util.function.Function.class);
                    var facts=(PlatformCommandExecutionApi.SuccessFacts)factory.apply(report);
                    assertTrue(facts.detailSnapshot().contains("ownerResults"));assertTrue(facts.detailSnapshot().contains("9101"));
                    assertNull(facts.eventType());assertTrue(facts.businessEvents().isEmpty());
                    return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,report);
                });
        var result=service.reconcile(100L,request,actor);
        assertEquals(9001L,result.report().targets().getFirst().targets().getFirst().targetId());
        verify(evidence).reconcile(any(),any(),eq("products"),anyList(),anyMap(),eq("PRODUCT_SCOPE_V4"),eq(Map.of("1",baseline)));
    }
    @Test void originalNullCannotBeDroppedFromCompleteSourceFields() {
        prepare();sourceRow.setPayloadJson(sourceRow.getPayloadJson().replace("\"projectQuantity\":null,",""));
        assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));verifyNoInteractions(scopes,commands);
    }
    @Test void deniedSourceAndWrongTargetCannotProduceSuccessfulReceipt() {
        prepare();when(sources.loadCorrectionSource(any(),any(),any())).thenThrow(new IllegalArgumentException("DENIED"));
        assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));verifyNoInteractions(scopes,commands);
    }
    @Test void targetIdentityMustBeTheApprovedExistingId() {
        prepare();when(scopes.apply(any())).thenReturn(List.of(new DppmsProjectProductScopeApi.Result("1",9002L,"CREATED",Map.of())));
        assertThrows(RuntimeException.class,()->service.reconcile(100L,request,actor));verifyNoInteractions(commands);
    }
    @Test void missingAuditBindingAndAmbiguousMappingCannotAdoptCurrentValues() {
        prepare();doReturn(Map.of("1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of()))).when(evidence).statuses(any(),any(),any(),anyList());
        assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));
        doReturn(Map.of("1",new ProjectLegacyNativeEvidenceService.RowStatus("AMBIGUOUS",null,null,List.of(),baseline))).when(evidence).statuses(any(),any(),any(),anyList());
        assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));verifyNoInteractions(scopes,commands);
    }
    @Test void wrongTenantOrSnapshotCannotReachCommerceOwner() {
        prepare();sourceRow.setTenantId(2L);assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));
        sourceRow.setTenantId(1L);sourceRow.setSnapshotId(21L);assertThrows(RuntimeException.class,()->service.preview(100L,request,actor));verifyNoInteractions(scopes,commands);
    }
}
