package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageContractReferenceTest {
    final P15DeliverySyncTask.TargetMapper mapper=mock(P15DeliverySyncTask.TargetMapper.class);
    final SyncIdentityStore identities=mock(SyncIdentityStore.class);
    final P15DeliverySyncTask task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class),org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.class));
    final P15DeliverySyncTask.Row parent=new P15DeliverySyncTask.Row().setId(20L).setTenantId(1L)
            .setSourceSystem("DPPMS").setSourceRecordKey("parent-uuid").setDeleted(false);
    final P15DeliverySyncTask.Row old=new P15DeliverySyncTask.Row().setId(10L).setTenantId(1L)
            .setSourceSystem("DPPMS").setSourceRecordKey("package-uuid").setPackageNo("package-uuid")
            .setShipmentContractSourceKey("parent-uuid").setDeleted(false).setUpdater("original")
            .setUpdateTime(LocalDateTime.of(2020,1,1,0,0));
    final Map<String,SyncIdentityStore.Identity> saved=new HashMap<>();

    SyncIdentityStore.Identity identity(String key,String kind,P15DeliverySyncTask.Row row){
        return new SyncIdentityStore.Identity(key,row.getId(),P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation(kind,row)));
    }
    SyncBatch<P15DeliverySyncTask.Source,P15DeliverySyncTask.Parameters> batch(boolean preview){
        saved.put("PACKAGE",identity("package-uuid","Package",old));
        saved.put("SHIPMENT_CONTRACT",identity("parent-uuid","ShipmentContract",parent));
        doAnswer(call->{var value=saved.get(call.<SyncIdentityStore.Query>getArgument(0).object());return value==null?Map.of():Map.of(value.sourceKey(),value);}).when(identities).findByKeys(any());
        when(mapper.selectPackages(any())).thenReturn(List.of(old));
        when(mapper.selectShipmentContracts(any())).thenReturn(List.of(parent));
        when(mapper.updatePackageContractReference(any())).thenReturn(1);
        var context=new SyncContext<Void>(1,2,3,preview,37,1000,LocalDateTime.of(2026,11,2,0,0),
                mock(SyncSource.class),identities,mock(SyncReferences.class),"PackageContractReference",mock(SqlSession.class),true,true)
                .forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
        return new SyncBatch<>(context,List.of(new P15DeliverySyncTask.Source().setKey("package-uuid").setTarget(old)));
    }
    @Test void exactOwnedParentIsFilledWithAuditPreservedAndReplayDoesNotWrite(){
        var b=batch(false);task.syncDataInsertBefore(b);var row=b.rows().getFirst();
        assertEquals(20L,row.getTarget().getShipmentContractRefId());assertNull(old.getShipmentContractRefId());
        assertEquals(old.getUpdateTime(),row.getTarget().getUpdateTime());assertEquals(old.getUpdater(),row.getTarget().getUpdater());
        task.syncDataInsert(b);task.syncDataInsertAfter(b);
        verify(identities).saveAll(argThat(values->values.size()==1&&values.getFirst().object().equals("PACKAGE")
                &&values.getFirst().previousFingerprint().equals(saved.get("PACKAGE").fingerprint())));
        old.setShipmentContractRefId(20L);var replay=batch(false);task.syncDataInsertBefore(replay);task.syncDataInsert(replay);
        assertEquals(SyncWriteAction.UNCHANGED,replay.rows().getFirst().getAction());verify(mapper,times(1)).updatePackageContractReference(any());
    }
    @Test void previewDoesNotWriteAndUnownedPackageIsNotAdopted(){
        var b=batch(true);task.syncDataInsertBefore(b);assertEquals(1,task.syncDataCount(b).updated());task.syncDataInsert(b);
        var unowned=batch(false);saved.remove("PACKAGE");task.syncDataInsertBefore(unowned);task.syncDataInsert(unowned);
        assertEquals(SyncWriteAction.UNCHANGED,unowned.rows().getFirst().getAction());verify(mapper,never()).updatePackageContractReference(any());
    }
    @Test void missingParentAndDifferentExistingReferenceRemainUnchanged(){
        for(String key:Arrays.asList(null,"missing")){
            old.setShipmentContractSourceKey(key);var b=batch(false);task.syncDataInsertBefore(b);task.syncDataInsert(b);
            assertEquals(1L,b.context().parameters().missing().get("PACKAGE_CONTRACT_REFERENCE_PENDING"));
        }
        old.setShipmentContractSourceKey("parent-uuid").setShipmentContractRefId(99L);var b=batch(false);task.syncDataInsertBefore(b);task.syncDataInsert(b);
        assertEquals(99L,old.getShipmentContractRefId());assertEquals(1L,b.context().parameters().missing().get("PACKAGE_CONTRACT_REFERENCE_DIFFERENT"));
        verify(mapper,never()).updatePackageContractReference(any());
    }
    @Test void changedHistoryParentAndCrossTenantTargetAreRejected(){
        var changed=batch(false);old.setUpdater("human edit");assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(changed));
        var parentChanged=batch(false);parent.setContractNo("changed");assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(parentChanged));
        var tenantChanged=batch(false);parent.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(tenantChanged));
    }
    @Test void concurrentUpdateCountMismatchFailsAndMaximumBatchParses()throws Exception{
        var b=batch(false);task.syncDataInsertBefore(b);when(mapper.updatePackageContractReference(any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsert(b));
        var config=OrganizationBackfillSqlTest.config("P15TargetMapper");
        var sql=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+".updatePackageContractReference")
                .getBoundSql(new SyncBatch<>(b.context(),Collections.nCopies(2000,b.rows().getFirst()))).getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(sql);
    }
}
