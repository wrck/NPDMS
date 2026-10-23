package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P14OrderCompanyCompletionTest {
    final P14SalesOrderSyncTask.TargetMapper mapper=mock(P14SalesOrderSyncTask.TargetMapper.class);
    final ManagedOrganizationApi organizations=mock(ManagedOrganizationApi.class);
    final SyncIdentityStore identities=mock(SyncIdentityStore.class);
    final P14SalesOrderSyncTask task=new P14SalesOrderSyncTask(mapper,organizations);
    final LocalDateTime historical=LocalDateTime.of(2020,1,1,0,0);

    ManagedOrganizationApi.Node company(long tenant,String code){
        var row=new CompanyDO().setId(100L).setCode(code).setName("公司");row.setTenantId(tenant);
        return new ManagedOrganizationApi.Node(row,null,null);
    }
    SyncContext<Void> context(){return new SyncContext<>(1,2,3,false,37,1000,LocalDateTime.of(2026,10,23,12,0),
        mock(SyncSource.class),identities,mock(SyncReferences.class),"BASE_P14",mock(SqlSession.class),false,true);}
    P14SalesOrderSyncTask.OrderRow old(String code){return new P14SalesOrderSyncTask.OrderRow().setId(10L).setTenantId(1L)
        .setSourceSystem("SAP").setCompanyCode(code).setOrderType("0").setOrderNo("ORDER").setDeleted(0).setVersion(7)
        .setCustomerName("原客户").setOrderComment("原备注").setSourceVersion("raw-version").setSourceUpdatedAt(historical)
        .setSourceSyncTime(historical).setCreator("historical").setUpdater("historical").setCreateTime(historical).setUpdateTime(historical);}
    SyncBatch<P14SalesOrderSyncTask.Source,P14SalesOrderSyncTask.Parameters> batch(P14SalesOrderSyncTask.OrderRow old){
        when(organizations.list()).thenReturn(List.of(company(1,"001")));
        var c=context();var parameters=task.createParameters(c);
        when(mapper.selectOrders(any())).thenReturn(List.of(old));
        when(identities.findByKeys(any())).thenReturn(Map.of("original-source-key",new SyncIdentityStore.Identity("original-source-key",10,P14SalesOrderSyncTask.fingerprint(old))));
        return new SyncBatch<>(c.forParameters(parameters).forLocalData("OrderCompany",false,true),
            new ArrayList<>(List.of(new P14SalesOrderSyncTask.Source().setKey("original-source-key").setOrder(old))));
    }
    @Test void fillsOwnedHeaderPreservesAllOtherFactsAndReplaysWithoutAnotherUpdate(){
        var old=old("001");var batch=batch(old);var source=batch.rows().getFirst();var previous=P14SalesOrderSyncTask.fingerprint(old);
        task.syncDataInsertBefore(batch);var next=source.getOrder();assertEquals(SyncWriteAction.UPDATE,source.getAction());
        assertEquals(100L,next.getCompanyId());assertEquals("公司",next.getCompanyName());assertEquals(8,next.getVersion());
        var expected=new P14SalesOrderSyncTask.OrderRow();org.springframework.beans.BeanUtils.copyProperties(old,expected);
        expected.setCompanyId(100L).setCompanyName("公司").setVersion(8);
        assertEquals(P14SalesOrderSyncTask.fingerprint(expected),P14SalesOrderSyncTask.fingerprint(next));
        assertNull(old.getCompanyId());assertEquals(historical,next.getUpdateTime());assertEquals("historical",next.getUpdater());
        when(mapper.updateOrderCompany(any())).thenReturn(1);task.syncDataInsert(batch);task.syncDataInsertAfter(batch);
        var saves=org.mockito.ArgumentCaptor.forClass(List.class);verify(identities).saveAll(saves.capture());
        var saved=(SyncIdentityStore.Save)saves.getValue().getFirst();assertEquals("SALES_ORDER",saved.object());assertEquals(previous,saved.previousFingerprint());
        when(mapper.selectOrders(any())).thenReturn(List.of(next));
        when(identities.findByKeys(any())).thenReturn(Map.of(source.getKey(),new SyncIdentityStore.Identity(source.getKey(),10,saved.fingerprint())));
        task.syncDataInsertBefore(batch);task.syncDataInsert(batch);assertEquals(SyncWriteAction.UNCHANGED,source.getAction());
        verify(mapper,times(1)).updateOrderCompany(any());verify(organizations,times(1)).list();
    }
    @Test void existingFieldsAreNeverOverwrittenAndMissingCompanyContinuesWithWarning(){
        var batch=batch(old("001").setCompanyId(999L).setCompanyName("既有名称"));task.syncDataInsertBefore(batch);
        assertEquals(SyncWriteAction.UNCHANGED,batch.rows().getFirst().getAction());assertEquals(999L,batch.rows().getFirst().getOrder().getCompanyId());
        var missing=batch(old("004"));task.syncDataInsertBefore(missing);assertEquals(SyncWriteAction.UNCHANGED,missing.rows().getFirst().getAction());
        var steps=new ArrayList<String>();var orchestration=new P14SalesOrderSyncTask(mapper,organizations){
            @Override protected List<Source> syncData(SyncContext<Parameters> c){steps.add(c.dataName());return List.of();}
        };
        orchestration.work(missing.context());assertEquals(List.of("Order","Line","OrderCompany"),steps);
        assertTrue(missing.context().runState().warnings().contains("P14_ORDER_COMPANY_DIRECTORY_MISSING:004:1"));
    }
    @Test void unownedRowsAreNotClaimedAndChangedOwnedRowsAreRejected(){
        var batch=batch(old("001"));when(identities.findByKeys(any())).thenReturn(Map.of());clearInvocations(mapper);
        task.syncDataInsertBefore(batch);task.syncDataInsert(batch);verifyNoInteractions(mapper);
        when(identities.findByKeys(any())).thenReturn(Map.of("original-source-key",new SyncIdentityStore.Identity("original-source-key",10,"changed")));
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));verify(mapper,never()).updateOrderCompany(any());
    }
    @Test void company004UsesTheSameDirectoryCompletionAndPreservesItsOriginalCode(){
        var old=old("004");var batch=batch(old);
        when(organizations.list()).thenReturn(List.of(company(1,"004")));
        var c=context();var parameters=task.createParameters(c);
        var completion=new SyncBatch<>(c.forParameters(parameters).forLocalData("OrderCompany",false,true),batch.rows());
        task.syncDataInsertBefore(completion);
        var source=completion.rows().getFirst();assertEquals(SyncWriteAction.UPDATE,source.getAction());
        assertEquals(100L,source.getOrder().getCompanyId());assertEquals("公司",source.getOrder().getCompanyName());
        assertEquals("004",source.getOrder().getCompanyCode());
    }
    @Test void directoryAndCurrentRowsCannotCrossTenants(){
        when(organizations.list()).thenReturn(List.of(company(2,"001")));
        assertThrows(IllegalArgumentException.class,()->task.createParameters(context()));
        var batch=batch(old("001"));when(mapper.selectOrders(any())).thenReturn(List.of(old("001").setTenantId(2L)));
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));
    }
    @Test void fillsOnlyTheMissingFieldAndRejectsBatchVersionConflict(){
        var batch=batch(old("001").setCompanyName("既有名称"));task.syncDataInsertBefore(batch);
        assertEquals("既有名称",batch.rows().getFirst().getOrder().getCompanyName());assertEquals(100L,batch.rows().getFirst().getOrder().getCompanyId());
        when(mapper.updateOrderCompany(any())).thenReturn(0);assertThrows(IllegalArgumentException.class,()->task.syncDataInsert(batch));
    }
    @Test void localSelectionAndFullBatchUpdateParseWithoutDatabaseAccess() throws Exception {
        var config=OrganizationBackfillSqlTest.config("P14TargetMapper");
        when(organizations.list()).thenReturn(List.of(company(1,"001")));var parameters=task.createParameters(context());
        var select=config.getMappedStatement(P14SalesOrderSyncTask.TargetMapper.class.getName()+".selectOrderCompany")
            .getBoundSql(new AbstractSyncTask.SourceQuery<>(2001,1,true,parameters)).getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(select);
        assertTrue(select.contains("b.source_key AS raw_key"));assertTrue(select.contains("b.deleted=FALSE"));
        assertTrue(select.contains("t.connection_id=?"));assertTrue(select.contains("r.tenant_id=?"));
        var rows=new ArrayList<P14SalesOrderSyncTask.Source>();
        for(long id=1;id<=2000;id++)rows.add(new P14SalesOrderSyncTask.Source().setExpectedVersion(7).setOrder(old("001").setId(id).setCompanyId(100L).setCompanyName("公司")));
        var sql=config.getMappedStatement(P14SalesOrderSyncTask.TargetMapper.class.getName()+".updateOrderCompany")
            .getBoundSql(new SyncBatch<>(context().forParameters(parameters),rows)).getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(sql);assertTrue(sql.contains("tenant_id=?"));assertTrue(sql.contains("deleted=0"));
        assertTrue(sql.contains("version=CASE id"));assertTrue(sql.contains("version=version+1,update_time=update_time"));
    }
}
