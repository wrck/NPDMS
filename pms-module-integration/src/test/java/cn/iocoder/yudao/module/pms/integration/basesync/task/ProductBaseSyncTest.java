package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductBaseSyncTest {
    SyncContext<Void> context(SyncSource source){return new SyncContext<Void>(1,2,3,true,500,100,LocalDateTime.of(2026,10,17,12,0),source,mock(SyncIdentityStore.class,CALLS_REAL_METHODS),mock(SyncReferences.class),"TEST",mock(org.apache.ibatis.session.SqlSession.class),false,true);}
    @Test void exactPricePrecisionPreserved(){var row=P11CrmProductSyncTask.ProductSyncRow.builder().productCode("A").listPrice(new BigDecimal("1234567890123.1234567890")).build();assertDoesNotThrow(()->P11CrmProductSyncTask.validate(row));row.setListPrice(new BigDecimal("12345678901234.1234567890"));assertThrows(IllegalArgumentException.class,()->P11CrmProductSyncTask.validate(row));}
    @Test void supplementDoesNotAlterCrmEvenWhenSourceLabelsConflict(){
        var mapper=mock(P12SupplementProductSyncTask.TargetMapper.class);var existing=P12SupplementProductSyncTask.ProductSyncRow.builder().id(10L).tenantId(1L).productCode("A").productName("CRM").build();
        when(mapper.selectSupplementTargets(any())).thenReturn(List.of(existing));
        var incoming=P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("A").productName("legacy").labelConflict(true).build();
        var task=new P12SupplementProductSyncTask(mapper);var batch=new SyncBatch<>(context(mock(SyncSource.class)),List.of(incoming));task.syncDataInsertBefore(batch);
        assertEquals(SyncWriteAction.UNCHANGED,batch.rows().getFirst().getSyncAction());
    }
    @Test void supplementRejectsConflictingNewLabels(){
        var mapper=mock(P12SupplementProductSyncTask.TargetMapper.class);when(mapper.selectSupplementTargets(any())).thenReturn(List.of());
        var incoming=P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("A").labelConflict(true).build();
        assertThrows(IllegalArgumentException.class,()->new P12SupplementProductSyncTask(mapper).syncDataInsertBefore(new SyncBatch<>(context(mock(SyncSource.class)),List.of(incoming))));
    }
    @Test void invalidCrmStateCannotSilentlyBecomeActive(){
        var incoming=P11CrmProductSyncTask.ProductSyncRow.builder().productCode("A").crmStateCode(9).build();
        assertThrows(IllegalArgumentException.class,()->new P11CrmProductSyncTask().syncDataInsertBefore(new SyncBatch<>(context(mock(SyncSource.class)),List.of(incoming))));
    }
    @Test void crmStreamingReleasesPreviousBatchCodesBeforeSourceReadCompletes(){
        var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);
        var sqlSession=mock(org.apache.ibatis.session.SqlSession.class);
        when(source.open(3)).thenReturn(session);when(session.sqlSession()).thenReturn(sqlSession);
        // A stub-only session must not retain invocation arguments and manufacture a memory leak.
        var target=mock(org.apache.ibatis.session.SqlSession.class,withSettings().stubOnly());
        when(target.selectList(anyString(),any())).thenThrow(new AssertionError("P11 must not pre-query target"));
        when(target.insert(anyString(),any())).thenThrow(new AssertionError("preview wrote products"));
        when(target.update(anyString(),any())).thenThrow(new AssertionError("preview updated products"));
        doAnswer(call->{
            org.apache.ibatis.session.ResultHandler<P11CrmProductSyncTask.ProductSyncRow> handler=call.getArgument(2);
            var oldest=deliverCrmProduct(handler,1);
            for(int i=2;i<=10_000;i++){
                deliverCrmProduct(handler,i);
                if(i==256 || i==10_000){
                    // Probe while the source callback is still live, not after its state has been discarded.
                    for(int attempt=0;attempt<3 && !oldest.refersTo(null);attempt++)System.gc();
                    assertTrue(oldest.refersTo(null),"a completed source batch must not leave its product codes retained");
                }
            }
            return null;
        }).when(sqlSession).select(anyString(),any(),any(org.apache.ibatis.session.ResultHandler.class));
        var context=new SyncContext<Void>(1,2,3,true,128,10_000,context(source).startedAt(),source,
                mock(SyncIdentityStore.class,CALLS_REAL_METHODS),mock(SyncReferences.class),"BASE_P11",
                target,false,true);
        assertEquals(new SyncCounts(10_000,0,0,0,0),new P11CrmProductSyncTask().execute(context));
        verify(session).close();
    }
    private static java.lang.ref.WeakReference<String> deliverCrmProduct(
            org.apache.ibatis.session.ResultHandler<P11CrmProductSyncTask.ProductSyncRow> handler,int index){
        String code="P"+String.format("%05d",index);
        var result=new org.apache.ibatis.executor.result.DefaultResultContext<P11CrmProductSyncTask.ProductSyncRow>();
        result.nextResultObject(P11CrmProductSyncTask.ProductSyncRow.builder().productCode(code).crmStateCode(0).build());
        handler.handleResult(result);
        return new java.lang.ref.WeakReference<>(code);
    }
    @Test void snapshotDetectsNormalizedDuplicateAcrossBatchesBeforeFirstInsert(){
        var target=mock(P12SupplementProductSyncTask.TargetMapper.class);
        when(target.selectSupplementTargets(any())).thenReturn(List.of());
        var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);
        var mapper=mock(org.apache.ibatis.session.SqlSession.class);
        when(source.open(3)).thenReturn(session);
        when(session.sqlSession()).thenReturn(mapper);
        doReturn(List.of(
                P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("A").productName("first").build(),
                P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("B").productName("B").build(),
                P12SupplementProductSyncTask.ProductSyncRow.builder().productCode(" A ").productName("conflict").build()))
                .when(mapper).selectList(anyString(),any());
        var context=new SyncContext<Void>(1,2,3,false,1,10,context(source).startedAt(),source,
                mock(SyncIdentityStore.class,CALLS_REAL_METHODS),mock(SyncReferences.class),"BASE_P12",mock(org.apache.ibatis.session.SqlSession.class),false,true);
        assertThrows(IllegalArgumentException.class,()->new P12SupplementProductSyncTask(target).execute(context));
        verify(context.target()).clearCache();
        verifyNoMoreInteractions(context.target());verify(session).close();
    }
}
