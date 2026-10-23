package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryOrganizationBackfillTest {
    @Test void reviewedOrganizationCompletionRunsAfterAllFiveRawSteps() {
        var mapper=mock(P15DeliverySyncTask.TargetMapper.class);
        when(mapper.packageSourceColumnReady()).thenReturn(1);
        var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);
        var sourceMapper=mock(P15DeliverySyncTask.SourceMapper.class);
        when(source.open(3)).thenReturn(session);when(session.mapper(P15DeliverySyncTask.SourceMapper.class)).thenReturn(sourceMapper);
        var steps=new ArrayList<String>();
        var task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class)) {
            @Override protected List<Source> syncData(SyncContext<Parameters> context) {
                steps.add(context.dataName());return List.of();
            }
        };
        var context=new SyncContext<Void>(1,2,3,true,37,1000,LocalDateTime.of(2026,10,23,12,0),source,
                mock(SyncIdentityStore.class),mock(SyncReferences.class),"BASE_P15",mock(SqlSession.class),false,true);
        task.work(context.forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3)));
        assertEquals(List.of("ShipmentContract","Package","ShipmentEvent","DeviceRelationship","Contract",
                "ContractOrganization","ShipmentContractOrganization"),steps);
    }

    @Test void ownedExistingDeviceNeedsItsContractOrganizationProjection() {
        var mapper=mock(P30DeviceBaseSyncTask.TargetMapper.class);
        var contracts=mock(ContractDeviceVisibilityApi.class);
        when(contracts.getOrganizations(1L,Set.of("CON"))).thenReturn(List.of(new ContractDeviceVisibilityApi.Organization("CON",100L,"公司",200L,"D","部门")));
        var identities=mock(SyncIdentityStore.class);
        var old=new P30DeviceBaseSyncTask.DeviceRow().setId(10L).setTenantId(1L).setSn("SN").setRequestedSn("SN")
                .setSourceSystem("DPPMS").setContractNo("CON").setVersion(0L).setProjectAssignmentVersion(0L).setDeleted(false);
        when(mapper.selectExisting(any())).thenReturn(List.of(old));
        when(identities.findByKeys(any())).thenReturn(Map.of("SN",new SyncIdentityStore.Identity("SN",10,P30DeviceBaseSyncTask.fingerprint(old))));
        var context=new SyncContext<Void>(1,2,3,true,37,1000,LocalDateTime.of(2026,10,23,12,0),mock(SyncSource.class),
                identities,mock(SyncReferences.class),"DeviceBase",mock(SqlSession.class),false,true)
                .forParameters(new P30DeviceBaseSyncTask.Parameters());
        var source=new P30DeviceBaseSyncTask.DeviceSource();source.setDeviceSn("SN");source.setTenantId(1L);source.setSourceSystem("DPPMS");
        new P30DeviceBaseSyncTask(mapper,contracts).syncDataInsertBefore(new SyncBatch<>(context,new ArrayList<>(List.of(source))));
        assertEquals(SyncWriteAction.UPDATE,source.getSyncAction());
        assertEquals(100L,source.getTarget().getCompanyId());assertEquals(200L,source.getTarget().getDepartmentId());
        assertNull(source.getTarget().getProjectId());assertEquals(P30DeviceBaseSyncTask.fingerprint(old),P30DeviceBaseSyncTask.fingerprint(source.getTarget()));
    }

    @Test void completionPreservesRawFactsRebaselinesOnlyOwnedBindingsAndReplaysWithoutAnUpdate() {
        var mapper=mock(P15DeliverySyncTask.TargetMapper.class);var identities=mock(SyncIdentityStore.class);
        var old=new P15DeliverySyncTask.Row().setId(10L).setTenantId(1L).setSourceSystem("DPPMS")
                .setSourceRecordKey("source").setContractNo("RAW-contract").setDepartmentCode("D").setDeleted(false)
                .setCustomerName("原客户").setRemark("原备注").setUpdater("historical").setUpdateTime(LocalDateTime.of(2020,1,1,0,0));
        var previous=P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation("ShipmentContract",old));
        when(identities.findByKeys(any())).thenReturn(Map.of("source",new SyncIdentityStore.Identity("source",10,previous)));
        when(mapper.selectShipmentContracts(any())).thenReturn(List.of(old));when(mapper.updateShipmentContractOrganization(any())).thenReturn(1);
        var parameters=directory();var context=deliveryContext(identities,parameters,false).forLocalData("ShipmentContractOrganization",false,true);
        var source=new P15DeliverySyncTask.Source().setKey("source").setTarget(old).setResolvedContractId(99L).setResolvedCompanyCode("001");
        var batch=new SyncBatch<>(context,new ArrayList<>(List.of(source)));var task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class));
        task.syncDataInsertBefore(batch);assertEquals(SyncWriteAction.UPDATE,source.getAction());
        assertEquals("RAW-contract",source.getTarget().getContractNo());assertEquals("原客户",source.getTarget().getCustomerName());
        assertEquals(old.getUpdateTime(),source.getTarget().getUpdateTime());assertEquals("historical",source.getTarget().getUpdater());
        assertEquals(99L,source.getTarget().getContractId());assertEquals(100L,source.getTarget().getCompanyId());assertEquals(200L,source.getTarget().getDepartmentId());
        task.syncDataInsert(batch);task.syncDataInsertAfter(batch);
        var saves=org.mockito.ArgumentCaptor.forClass(List.class);verify(identities).saveAll(saves.capture());
        var saved=(SyncIdentityStore.Save)saves.getValue().getFirst();assertEquals("SHIPMENT_CONTRACT",saved.object());assertEquals(previous,saved.previousFingerprint());
        when(identities.findByKeys(any())).thenReturn(Map.of("source",new SyncIdentityStore.Identity("source",10,saved.fingerprint())));
        when(mapper.selectShipmentContracts(any())).thenReturn(List.of(source.getTarget()));
        task.syncDataInsertBefore(batch);assertEquals(SyncWriteAction.UNCHANGED,source.getAction());task.syncDataInsert(batch);
        verify(mapper,times(1)).updateShipmentContractOrganization(any());
    }

    @Test void absentUniqueContractDoesNotBlockRawAcceptanceOrIndependentDepartmentCompletion() {
        var mapper=mock(P15DeliverySyncTask.TargetMapper.class);var identities=mock(SyncIdentityStore.class);
        var old=new P15DeliverySyncTask.Row().setId(10L).setTenantId(1L).setSourceSystem("DPPMS").setSourceRecordKey("source")
                .setContractNo("missing").setDepartmentCode("D").setDeleted(false);
        when(identities.findByKeys(any())).thenReturn(Map.of("source",new SyncIdentityStore.Identity("source",10,
                P15DeliverySyncTask.fingerprint(new P15DeliverySyncTask.Validation("ShipmentContract",old)))));
        when(mapper.selectShipmentContracts(any())).thenReturn(List.of(old));var parameters=directory();
        var source=new P15DeliverySyncTask.Source().setKey("source").setTarget(old);
        new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class)).syncDataInsertBefore(new SyncBatch<>(
                deliveryContext(identities,parameters,true).forLocalData("ShipmentContractOrganization",false,true),new ArrayList<>(List.of(source))));
        assertNull(source.getTarget().getCompanyCode());assertNull(source.getTarget().getContractId());
        assertEquals("missing",source.getTarget().getContractNo());assertEquals(200L,source.getTarget().getDepartmentId());
        assertEquals(1L,parameters.missing().get("SHIPMENT_CONTRACT_UNRESOLVED"));
    }

    @Test void unownedAndExternallyChangedRowsCannotBeClaimedByCompletion() {
        var mapper=mock(P15DeliverySyncTask.TargetMapper.class);var identities=mock(SyncIdentityStore.class);
        when(identities.findByKeys(any())).thenReturn(Map.of());
        var old=new P15DeliverySyncTask.Row().setId(10L).setTenantId(1L).setSourceRecordKey("source").setDeleted(false);
        var row=new P15DeliverySyncTask.Source().setKey("source").setTarget(old);
        var batch=new SyncBatch<>(deliveryContext(identities,directory(),false).forLocalData("ShipmentContractOrganization",false,true),new ArrayList<>(List.of(row)));
        var task=new P15DeliverySyncTask(mapper,mock(ManagedOrganizationApi.class));task.syncDataInsertBefore(batch);task.syncDataInsert(batch);
        assertEquals(SyncWriteAction.UNCHANGED,row.getAction());verifyNoInteractions(mapper);
        when(identities.findByKeys(any())).thenReturn(Map.of("source",new SyncIdentityStore.Identity("source",10,"different")));
        when(mapper.selectShipmentContracts(any())).thenReturn(List.of(old));
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(batch));verify(mapper,never()).updateShipmentContractOrganization(any());
    }

    @Test void deviceContractFactsStayExactAndDoNotOverrideProjectAssignmentsOrUnownedDevices() {
        for(String condition:List.of("different-case","project","unowned","missing")){
            var mapper=mock(P30DeviceBaseSyncTask.TargetMapper.class);var identities=mock(SyncIdentityStore.class);var contracts=mock(ContractDeviceVisibilityApi.class);
            var old=new P30DeviceBaseSyncTask.DeviceRow().setId(10L).setTenantId(1L).setSn("SN").setRequestedSn("SN").setSourceSystem("DPPMS")
                    .setContractNo("CON").setVersion(0L).setProjectAssignmentVersion(0L).setDeleted(false).setProjectId(condition.equals("project")?99L:null);
            when(mapper.selectExisting(any())).thenReturn(List.of(old));
            when(identities.findByKeys(any())).thenReturn(condition.equals("unowned")?Map.of():Map.of("SN",new SyncIdentityStore.Identity("SN",10,P30DeviceBaseSyncTask.fingerprint(old))));
            when(contracts.getOrganizations(anyLong(),anySet())).thenReturn(condition.equals("missing")?List.of():List.of(new ContractDeviceVisibilityApi.Organization("con",100L,"公司",null,null,null)));
            var source=new P30DeviceBaseSyncTask.DeviceSource();source.setDeviceSn("SN");source.setTenantId(1L);source.setSourceSystem("DPPMS");
            var context=new SyncContext<Void>(1,2,3,true,37,1000,LocalDateTime.of(2026,10,23,12,0),mock(SyncSource.class),identities,
                    mock(SyncReferences.class),"DeviceBase",mock(SqlSession.class),false,true).forParameters(new P30DeviceBaseSyncTask.Parameters());
            new P30DeviceBaseSyncTask(mapper,contracts).syncDataInsertBefore(new SyncBatch<>(context,new ArrayList<>(List.of(source))));
            assertEquals(SyncWriteAction.UNCHANGED,source.getSyncAction(),condition);assertNull(source.getTarget().getCompanyId());
            assertEquals(old.getProjectId(),source.getTarget().getProjectId());
            if(condition.equals("project")||condition.equals("unowned"))verifyNoInteractions(contracts);
        }
    }

    static P15DeliverySyncTask.Parameters directory(){
        var company=new cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO().setId(100L).setCode("001").setName("公司");company.setTenantId(1L);
        var department=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(200L).setCode("D").setName("部门");department.setTenantId(1L);
        return new P15DeliverySyncTask.Parameters(Map.of(),3,Map.of(
                new P15DeliverySyncTask.OrganizationKey("COMPANY","001"),new ManagedOrganizationApi.Node(company,null,null),
                new P15DeliverySyncTask.OrganizationKey("DEPARTMENT","D"),new ManagedOrganizationApi.Node(null,department,null)),new LinkedHashMap<>());
    }
    static SyncContext<P15DeliverySyncTask.Parameters> deliveryContext(SyncIdentityStore identities,P15DeliverySyncTask.Parameters parameters,boolean preview){
        return new SyncContext<Void>(1,2,3,preview,37,1000,LocalDateTime.of(2026,10,23,12,0),mock(SyncSource.class),identities,
                mock(SyncReferences.class),"BASE_P15",mock(SqlSession.class),false,true).forParameters(parameters);
    }
}
