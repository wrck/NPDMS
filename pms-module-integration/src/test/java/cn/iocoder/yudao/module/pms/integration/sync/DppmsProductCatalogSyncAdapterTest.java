package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsProductCatalogSyncAdapterTest {
    final ProductCatalogMigrationApi products=mock(ProductCatalogMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsProductCatalogSyncAdapter adapter=new DppmsProductCatalogSyncAdapter(products,tasks,runs);
    SyncTaskDO task; SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setAdapter("DPPMS_PRODUCT_CATALOG").setSourceSystem("DPPMS");
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);run.setTenantId(1L);run.setCreator("7");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Row row() {
        var fields=new LinkedHashMap<String,Object>();fields.put("id",null);fields.put("item"," CODE ");fields.put("itemname",null);
        fields.put("describe_"," raw name ");
        return new Row("fb_items"," CODE ",fields,null);
    }
    Batch batch(List<Row> rows,List<Binding> bindings) { return new Batch("integration:1:12",rows,bindings,false,"RETAIN",false,"UPSERT",false); }
    @Test void streamingChunksUseTheRealRunActorAndProductCodeWithEveryOriginalColumnRead() {
        run.setStatus("READING").setPreview(true);
        when(products.preview(any())).thenAnswer(invocation->{
            Request request=invocation.getArgument(0);assertEquals(7L,request.actorId());
            assertEquals("integration-run:13",request.correlationId());assertEquals(" CODE ",request.entries().getFirst().sourceKey());
            assertNull(request.entries().getFirst().label().productModel());assertEquals(" raw name ",request.entries().getFirst().label().productName());
            return List.of(new Result(" CODE ",10L,"UPDATE",null,new Snapshot(10L,"CODE",List.of(request.entries().getFirst().label()))));
        });
        var changes=adapter.preview(batch(List.of(row()),List.of()));assertEquals(10L,changes.getFirst().targetId());assertTrue(changes.getFirst().after().containsKey("product"));
        verify(products,never()).apply(any());
    }
    @Test void realExecutionPhasesAreAcceptedOnlyForTheMatchingOperation() {
        when(products.preview(any())).thenReturn(List.of());when(products.apply(any())).thenReturn(List.of());
        for (String status:List.of("READING","VALIDATING")) {
            run.setStatus(status).setPreview(true);assertDoesNotThrow(()->adapter.preview(batch(List.of(row()),List.of())));
        }
        for (String status:List.of("READING","APPLYING")) {
            run.setStatus(status).setPreview(false);assertDoesNotThrow(()->adapter.apply(batch(List.of(row()),List.of())));
        }
        clearInvocations(products);
        for (String status:List.of("QUEUED","SUCCESS","FAILED","PREVIEW_READY","RUNNING")) {
            run.setStatus(status).setPreview(true);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(row()),List.of())));
        }
        run.setStatus("READING").setPreview(true);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row()),List.of())));
        run.setStatus("READING").setPreview(false);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(row()),List.of())));
        verifyNoInteractions(products);
    }
    @Test void snapshotPreviewStartsATransactionBeforeCallingTheMandatoryOwner() {
        run.setStatus("VALIDATING").setPreview(true);
        when(products.preview(any())).thenAnswer(call->{
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return List.of();
        });
        var factory=new org.springframework.aop.framework.ProxyFactory(adapter);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                new org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager(),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        ((cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter)factory.getProxy()).preview(batch(List.of(row()),List.of()));
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        verify(products).preview(any());verify(products,never()).apply(any());
    }
    @Test void scheduledOrStaleRunsCannotInventAnActorOrUseChangedConfiguration() {
        run.setCreator("scheduled_sync");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row()),List.of())));
        run.setCreator("7");run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row()),List.of())));
        run.setConfigVersion(1);run.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row()),List.of())));
        verifyNoInteractions(products);
    }
    @Test void extractionByTechnicalIdMissingOriginalFieldsAndUnknownBindingEvidenceAreRejected() {
        var invalid=row();assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(new Row("fb_items","1",invalid.fields(),null)),List.of())));
        invalid.fields().remove("id");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(invalid),List.of())));
        var row=row();var bound=new Row(row.object(),row.sourceKey(),row.fields(),10L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(bound),List.of(new Binding("fb_items"," CODE ",10L,Map.of())))));
        verifyNoInteractions(products);
    }
    @Test void bindingsPreserveTheFormalSnapshotForReplayAndManualChangeChecks() {
        var row=row();var label=new Label(" CODE ","old",null);var snapshot=new Snapshot(10L,"CODE",List.of(label));
        when(products.apply(any())).thenAnswer(invocation->{
            Request request=invocation.getArgument(0);assertEquals(snapshot,request.entries().getFirst().lastApplied());
            return List.of(new Result(" CODE ",10L,"UNCHANGED",snapshot,snapshot));
        });
        adapter.apply(batch(List.of(new Row(row.object(),row.sourceKey(),row.fields(),10L)),List.of(new Binding("fb_items"," CODE ",10L,Map.of("product",snapshot)))));
        verify(products).apply(any());
    }
}
