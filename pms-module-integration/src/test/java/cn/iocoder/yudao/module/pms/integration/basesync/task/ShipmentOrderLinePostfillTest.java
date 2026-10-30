package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentOrderLinePostfillTest {
    final P15DeliverySyncTask.TargetMapper mapper=mock(P15DeliverySyncTask.TargetMapper.class);
    final SyncIdentityStore identities=mock(SyncIdentityStore.class);
    final P15DeliverySyncTask task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class));
    final P15DeliverySyncTask.Row old=new P15DeliverySyncTask.Row().setId(10L).setTenantId(1L)
            .setSourceSystem("DPPMS").setSourceKey("78897").setOrderNo("SO064298").setLineNo("10625024")
            .setProductCode("child-product").setDeleted(false).setVersion(4L).setUpdater("original")
            .setUpdateTime(LocalDateTime.of(2020,1,1,0,0));

    SyncBatch<P15DeliverySyncTask.Source,P15DeliverySyncTask.Parameters> batch(boolean preview) {
        when(mapper.selectEvents(any())).thenReturn(List.of(old));
        when(identities.findByKeys(any())).thenReturn(Map.of("78897",new SyncIdentityStore.Identity("78897",10,
                P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation("ShipmentEvent",old)))));
        when(mapper.selectShipmentOrderLines(any())).thenReturn(List.of(new P15DeliverySyncTask.ResolvedOrderLine()
                .setShipmentKey("78897").setOrderLineId(99L)));
        when(mapper.updateShipmentOrderLine(any())).thenReturn(1);
        var context=new SyncContext<Void>(1,2,3,preview,37,1000,LocalDateTime.of(2026,10,23,12,0),
                mock(SyncSource.class),identities,mock(SyncReferences.class),"ShipmentOrderLine",mock(SqlSession.class),false,true)
                .forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
        var source=new P15DeliverySyncTask.Source().setKey("78897").setRawOrderNo("SO064298").setRawLineNo("10625024")
                .setLineCandidateCount(1L).setLineSourceKey("p14-key").setLineSourceSystem("D365")
                .setLineCompanyCode("001").setLineType("0").setFormalLineNo("1").setLineProductCode("parent-product");
        return new SyncBatch<>(context,new ArrayList<>(List.of(source)));
    }

    @Test void exactBoundLineIsPostfilledWithoutChangingOriginalFactsAndReplayDoesNotWrite() {
        var batch=batch(false);task.syncDataInsertBefore(batch);var source=batch.rows().getFirst();
        assertEquals(SyncWriteAction.UPDATE,source.getAction());assertEquals(99L,source.getTarget().getOrderLineId());
        assertEquals(old.getVersion(),source.getTarget().getVersion());assertEquals(old.getUpdateTime(),source.getTarget().getUpdateTime());
        assertEquals("10625024",source.getTarget().getLineNo());assertEquals("child-product",source.getTarget().getProductCode());
        task.syncDataInsert(batch);task.syncDataInsertAfter(batch);
        verify(mapper).updateShipmentOrderLine(argThat(value->value.context().batchSize()==37&&value.rows().size()==1));
        verify(identities).saveAll(argThat(saves->saves.size()==1&&saves.getFirst().object().equals("SHIPMENT_EVENT")
                &&saves.getFirst().previousFingerprint().equals(P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation("ShipmentEvent",old)))));
        old.setOrderLineId(99L);var replay=batch(false);task.syncDataInsertBefore(replay);task.syncDataInsert(replay);
        assertEquals(SyncWriteAction.UNCHANGED,replay.rows().getFirst().getAction());verify(mapper,times(1)).updateShipmentOrderLine(any());
    }

    @Test void missingAndAmbiguousRawLinesRemainUnchangedWithoutCallingFormalResolver() {
        for(Long count:Arrays.asList(null,2L)){
            clearInvocations(mapper);var batch=batch(false);batch.rows().getFirst().setLineCandidateCount(count);
            task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
            assertEquals(SyncWriteAction.UNCHANGED,batch.rows().getFirst().getAction());assertNull(old.getOrderLineId());
            assertEquals(1,batch.context().parameters().missing().size());verify(mapper,never()).selectShipmentOrderLines(any());
            verify(mapper,never()).updateShipmentOrderLine(any());
        }
    }

    @Test void missingFormalLineAndChangedRawIdentityAreRecordedWithoutBlocking() {
        var batch=batch(false);when(mapper.selectShipmentOrderLines(any())).thenReturn(List.of());
        task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
        assertEquals(1L,batch.context().parameters().missing().get("SHIPMENT_ORDER_LINE_FORMAL_LINE_MISSING"));
        var changed=batch(false);changed.rows().getFirst().setRawLineNo("another");task.syncDataInsertBefore(changed);
        assertEquals(1L,changed.context().parameters().missing().get("SHIPMENT_ORDER_LINE_RAW_IDENTITY_CHANGED"));
        verify(mapper,never()).updateShipmentOrderLine(any());
    }

    @Test void existingDifferentReferenceAndUnownedShipmentAreNeverOverwritten() {
        old.setOrderLineId(77L);var batch=batch(false);task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
        assertEquals(77L,old.getOrderLineId());assertEquals(1L,batch.context().parameters().missing().get("SHIPMENT_ORDER_LINE_EXISTING_REFERENCE_DIFFERENT"));
        var unowned=batch(false);when(identities.findByKeys(any())).thenReturn(Map.of());task.syncDataInsertBefore(unowned);task.syncDataInsert(unowned);
        assertEquals(1L,unowned.context().parameters().missing().get("SHIPMENT_ORDER_LINE_SHIPMENT_NOT_OWNED"));
        verify(mapper,never()).updateShipmentOrderLine(any());
    }

    @Test void tamperedHistoryAndAffectedRowMismatchStillFailProtection() {
        var batch=batch(false);old.setUpdater("later-human");assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));
        var changed=batch(false);task.syncDataInsertBefore(changed);when(mapper.updateShipmentOrderLine(any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsert(changed));
    }

    @Test void previewCanCountCandidateButCannotWrite() {
        var batch=batch(true);task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
        assertEquals(1L,task.syncDataCount(batch).updated());verify(mapper,never()).updateShipmentOrderLine(any());
    }

    @Test void maximumFrontendBatchUsesOneNativeUpdateAndResolverParsesAtRuntimeStackSize() throws Exception {
        var configuration=OrganizationBackfillSqlTest.config("P15TargetMapper");var batch=batch(false);
        var rows=new ArrayList<P15DeliverySyncTask.Source>();
        for(int i=0;i<2000;i++)rows.add(batch.rows().getFirst().setTarget(old));
        var namespace=P15DeliverySyncTask.TargetMapper.class.getName()+".";
        var update=configuration.getMappedStatement(namespace+"updateShipmentOrderLine").getBoundSql(new SyncBatch<>(batch.context(),rows)).getSql();
        var select=configuration.getMappedStatement(namespace+"selectShipmentOrderLines").getBoundSql(Map.of("query",new P15DeliverySyncTask.ShipmentOrderLineQuery(1,3,rows))).getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(update);OrganizationBackfillSqlTest.parseWithRuntimeStack(select);
        assertTrue(update.contains("s.order_line_id IS NULL"));assertTrue(update.contains("s.update_time=s.update_time"));
    }
}
