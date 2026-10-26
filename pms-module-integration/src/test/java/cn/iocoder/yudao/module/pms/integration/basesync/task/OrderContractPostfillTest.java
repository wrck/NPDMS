package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderContractPostfillTest {
    final P15DeliverySyncTask.TargetMapper mapper=mock(P15DeliverySyncTask.TargetMapper.class);
    final SyncIdentityStore identities=mock(SyncIdentityStore.class);
    final SqlSession target=mock(SqlSession.class);
    final P15DeliverySyncTask task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class));
    final LocalDateTime originalDate=LocalDateTime.of(2023,2,24,11,19,34);

    SyncBatch<P15DeliverySyncTask.Source,P15DeliverySyncTask.Parameters> batch(boolean preview) {
        when(identities.findByKeys(any())).thenReturn(Map.of());
        when(mapper.selectOrderContractRelations(any())).thenReturn(List.of());
        when(target.insert(anyString(),any())).thenReturn(1);
        var context=new SyncContext<Void>(1,2,3,preview,2000,1000,LocalDateTime.of(2026,10,24,0,0),
                mock(SyncSource.class),identities,mock(SyncReferences.class),"OrderContractRelation",target,false,true)
                .forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
        var row=new P15DeliverySyncTask.OrderContractRow().setOrderId(10L).setContractId(20L)
                .setRelationRole("RELATED").setRelationSource("D365").setSourceSystem("D365")
                .setSalesOrderSourceKey("BASE_P14|ORDER|D365|001|0|SO064298").setContractSourceKey("[\"001\", \"31220230224A04\"]")
                .setSourceVersion("source-version").setSourceEvidence("{\"orderNo\":\"SO064298\"}").setEffectiveFrom(originalDate);
        return new SyncBatch<>(context,new ArrayList<>(List.of(new P15DeliverySyncTask.Source().setKey("relation-source").setRelation(row))));
    }

    @Test void createsUsingOriginalOrderDateAndReplaysWithoutWriting() {
        var batch=batch(false);task.syncDataInsertBefore(batch);var created=batch.rows().getFirst().getRelation();
        assertEquals(originalDate,created.getEffectiveFrom());assertNull(created.getEffectiveTo());
        assertEquals(SyncWriteAction.INSERT,batch.rows().getFirst().getAction());
        task.syncDataInsert(batch);task.syncDataInsertAfter(batch);
        verify(identities).saveAll(argThat(rows->rows.size()==1&&rows.getFirst().object().equals("ORDER_CONTRACT_RELATION")));
        var replay=batch(false);when(mapper.selectOrderContractRelations(any())).thenReturn(List.of(created));
        when(identities.findByKeys(any())).thenReturn(Map.of("relation-source",new SyncIdentityStore.Identity("relation-source",created.getId(),P15DeliverySyncTask.orderContractFingerprint(created))));
        task.syncDataInsertBefore(replay);task.syncDataInsert(replay);assertEquals(SyncWriteAction.UNCHANGED,replay.rows().getFirst().getAction());
        verify(target,times(1)).insert(anyString(),any());verify(target,never()).update(anyString(),any());
    }

    @Test void missingContractOrDateDoesNotBlockOtherRows() {
        var batch=batch(false);var missing=batch.rows().getFirst().getRelation();missing.setContractId(null);
        task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
        assertEquals(1L,batch.context().parameters().missing().get("ORDER_CONTRACT_CONTRACT_MISSING"));
        var noDate=batch(false);noDate.rows().getFirst().getRelation().setEffectiveFrom(null);
        task.syncDataInsertBefore(noDate);task.syncDataInsert(noDate);
        assertEquals(1L,noDate.context().parameters().missing().get("ORDER_CONTRACT_ORDER_DATE_MISSING"));
        verify(target,never()).insert(anyString(),any());
    }

    @Test void preexistingUnownedOrEndedRelationIsNeverTakenOver() {
        var batch=batch(false);var old=batch.rows().getFirst().getRelation();old.setId(30L).setTenantId(1L)
                .setDeleted(false).setEffectiveTo(originalDate.plusDays(1));
        when(mapper.selectOrderContractRelations(any())).thenReturn(List.of(old));
        task.syncDataInsertBefore(batch);task.syncDataInsert(batch);task.syncDataInsertAfter(batch);
        assertEquals(1L,batch.context().parameters().missing().get("ORDER_CONTRACT_EXISTING_RELATION_NOT_OWNED"));
        verify(identities).saveAll(List.of());verify(target,never()).insert(anyString(),any());
        assertEquals(originalDate.plusDays(1),old.getEffectiveTo());
    }

    @Test void alteredOwnedRelationAndMissingBoundTargetStillFail() {
        var batch=batch(false);var old=batch.rows().getFirst().getRelation().setId(30L).setTenantId(1L).setDeleted(false);
        var identity=new SyncIdentityStore.Identity("relation-source",30,P15DeliverySyncTask.orderContractFingerprint(old));
        when(identities.findByKeys(any())).thenReturn(Map.of("relation-source",identity));
        when(mapper.selectOrderContractRelations(any())).thenReturn(List.of(old));old.setUpdater("human");
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));
        when(mapper.selectOrderContractRelations(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));
    }

    @Test void previewDoesNotInsertAndActualAffectedCountMismatchFails() {
        var preview=batch(true);task.syncDataInsertBefore(preview);task.syncDataInsert(preview);
        assertEquals(1,task.syncDataCount(preview).inserted());verify(target,never()).insert(anyString(),any());
        var actual=batch(false);task.syncDataInsertBefore(actual);when(target.insert(anyString(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->task.syncDataInsert(actual));
    }

    @Test void nativeQueriesAndMaximumBatchParseWithTenantInterceptorAndRuntimeStack() throws Exception {
        var config=OrganizationBackfillSqlTest.config("P15TargetMapper");var batch=batch(false);
        var rows=new ArrayList<P15DeliverySyncTask.Source>();for(int i=0;i<2000;i++)rows.add(batch.rows().getFirst());
        String namespace=P15DeliverySyncTask.TargetMapper.class.getName()+".";
        var source=config.getMappedStatement(namespace+"selectOrderContractRelation").getBoundSql(
                new AbstractSyncTask.SourceQuery<>(100000,1,true,batch.context().parameters())).getSql();
        var lookup=config.getMappedStatement(namespace+"selectOrderContractRelations").getBoundSql(
                Map.of("query",new P15DeliverySyncTask.OrderContractQuery(1,rows,true))).getSql();
        var insert=config.getMappedStatement(namespace+"insertOrderContractRelation").getBoundSql(new SyncBatch<>(batch.context(),rows)).getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(source);OrganizationBackfillSqlTest.parseWithRuntimeStack(lookup);OrganizationBackfillSqlTest.parseWithRuntimeStack(insert);
    }
}
