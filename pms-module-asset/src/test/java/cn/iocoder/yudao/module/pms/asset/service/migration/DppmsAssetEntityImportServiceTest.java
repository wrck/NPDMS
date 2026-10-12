package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DppmsAssetEntityImportServiceTest {
    @Mock DeviceMapper devices;
    @Mock DeviceMigrationIdentityMapper identities;
    @Mock DeviceShipmentMapper shipments;
    @Mock DeviceAssignmentMapper relations;
    @Mock DeviceProjectAssignmentService assignments;
    @Mock DeviceWarrantyRecordMapper warranties;
    @Mock ProjectDeviceAssignmentGuardApi guard;
    @Mock cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionMapper factories;
    DppmsAssetEntityImportService service;
    LocalDateTime at=LocalDateTime.of(2024,1,2,0,0);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service=new DppmsAssetEntityImportService(devices,identities,shipments,relations,assignments,warranties,guard,factories);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    Command command(List<Software> software) {
        return new Command(1L,1L,100L,"TEST-A",200L,"a".repeat(64),at,
                List.of(new Shipment("301","TEST-SN","PACK","CODE","MODEL","NAME")),software,List.of());
    }
    void allowed() { when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null)); }
    DeviceShipmentDO shipment(String contract) {
        var row=new DeviceShipmentDO();row.setId(400L);row.setDeviceSn("TEST-SN");row.setContractNo(contract);return row;
    }
    DeviceDO device(Long project) {
        var row=new DeviceDO();row.setId(500L);row.setSn("TEST-SN");row.setContractNo("TEST-A-L");row.setProjectId(project);return row;
    }
    void reconciledDevice() {
        allowed();when(shipments.selectBySourceForUpdate(any())).thenReturn(shipment("TEST-A"));
        when(identities.selectIdentityForUpdate(any())).thenReturn(device(100L));
        var relation=new DeviceProjectRelationshipDO();relation.setId(600L);relation.setProjectId(100L);
        when(relations.selectCurrentProject(any())).thenReturn(relation);
    }
    @Test void deletedSoftwareEventCannotBeRecreatedOrPromoted() {
        allowed();var software=new Software("701","TEST-SN","LOG",at,"V1",null,null,null,"B1","C1","P1",true);
        var failure=assertThrows(IllegalArgumentException.class,()->service.importEntities(command(List.of(software))));
        assertEquals("DPPMS_SOFTWARE_REQUIRES_FORMAL_HISTORY_OWNER",failure.getMessage());
        verifyNoInteractions(devices,identities,shipments,relations,assignments,warranties,factories);
    }
    @Test void deletedWarrantyIdentityCannotCauseASecondInsert() {
        reconciledDevice();var warranty=new DeviceWarrantyRecordDO();warranty.setDeleted(true);
        when(warranties.selectSourceIdentity(any())).thenReturn(warranty);
        var base=command(List.of());var input=new Command(base.tenantId(),base.actorId(),base.projectId(),base.contractNo(),
                base.snapshotId(),base.sourceVersion(),base.extractedAt(),base.shipments(),List.of(),
                List.of(new Warranty("801","TEST-SN",at.toLocalDate(),at.plusYears(3).toLocalDate(),36,"GRADE","TEST-A")));
        assertEquals("TARGET_IDENTITY_CONFLICT",service.importEntities(input).getLast().reasonCode());
        verify(warranties,never()).insert(any(DeviceWarrantyRecordDO.class));
    }
    @Test void deniesForeignTenantBeforeAnyOwnerLookup() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.importEntities(command(List.of())));
        verifyNoInteractions(guard,devices,identities,shipments,assignments);
    }
    @Test void rejectsUnrelatedSourceShipmentWithoutCreatingADevice() {
        allowed();when(shipments.selectBySourceForUpdate(any())).thenReturn(shipment("OTHER"));
        assertEquals("SHIPMENT_SOURCE_IDENTITY_UNRESOLVED",service.importEntities(command(List.of())).getFirst().reasonCode());
        verifyNoInteractions(devices,identities,assignments);
    }
    @Test void preservesCurrentOwnershipAndNeverReassignsAnotherProject() {
        allowed();when(shipments.selectBySourceForUpdate(any())).thenReturn(shipment("TEST-A"));
        when(identities.selectIdentityForUpdate(any())).thenReturn(device(101L));
        assertEquals("DEVICE_CURRENT_OWNER_CONFLICT",service.importEntities(command(List.of())).getFirst().reasonCode());
        verifyNoInteractions(devices,assignments);
    }
    @Test void keepsDeletedIdentityDeleted() {
        allowed();when(shipments.selectBySourceForUpdate(any())).thenReturn(shipment("TEST-A"));
        var row=device(null);row.setDeleted(true);when(identities.selectIdentityForUpdate(any())).thenReturn(row);
        assertEquals("TARGET_IDENTITY_CONFLICT",service.importEntities(command(List.of())).getFirst().reasonCode());
        verifyNoInteractions(devices,assignments);
    }
    @Test void laterShipmentPreventsCreatingAFalseCurrentOwner() {
        allowed();when(shipments.selectBySourceForUpdate(any())).thenReturn(shipment("TEST-A"));
        when(shipments.selectLatest(any())).thenReturn(shipment("OTHER"));
        assertEquals("LATER_SHIPMENT_CONFLICT",service.importEntities(command(List.of())).getFirst().reasonCode());
        verifyNoInteractions(devices,assignments);
    }
    @Test void replacedSoftwareIsHistoryRatherThanRevocationOrCurrentProjection() {
        allowed();var software=new Software("701","TEST-SN","LOG",at,"V1",null,null,null,"B1","C1","P1",false);
        var failure=assertThrows(IllegalArgumentException.class,()->service.importEntities(command(List.of(software))));
        assertEquals("DPPMS_SOFTWARE_REQUIRES_FORMAL_HISTORY_OWNER",failure.getMessage());
        verifyNoInteractions(devices,identities,shipments,relations,assignments,warranties,factories);
    }
    Command factoryCommand() {
        var base=command(List.of());
        return new Command(base.tenantId(),base.actorId(),base.projectId(),base.contractNo(),base.snapshotId(),
                base.sourceVersion(),base.extractedAt(),base.shipments(),List.of(),List.of(),
                List.of(new FactoryVersion("TEST-SN","TEST-SN","CONP-FACTORY","BOOT","CPLD","PCB")));
    }
    @Test void factoryBaselineRequiresNoInventedSoftwareEventTime() {
        reconciledDevice();var result=service.importEntities(factoryCommand());
        var capture=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionDO.class);
        verify(factories).insert(capture.capture());assertEquals("CONP-FACTORY",capture.getValue().getConpVersion());
        assertNull(capture.getValue().getSourceUpdatedAt());
        assertEquals("ast_device_factory_version",result.getLast().targets().getFirst().targetTable());
    }
    @Test void deletedFactoryBaselineCannotBeRevived() {
        reconciledDevice();var row=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionDO();
        row.setDeleted(true);when(factories.selectIdentity(any())).thenReturn(row);
        assertEquals("TARGET_IDENTITY_CONFLICT",service.importEntities(factoryCommand()).getLast().reasonCode());
        verify(factories,never()).insert(any(cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionDO.class));
    }
    @Test void approvedCurrentProjectAllowsHistoricalShipmentEnrichmentWhilePreservingSelectedShipmentAndContract() {
        allowed();var historical=shipment("TEST-A");when(shipments.selectBySourceForUpdate(any())).thenReturn(historical);
        var owned=device(100L);owned.setContractNo("CURRENT-CONTRACT");owned.setShipmentRecordId(999L);
        when(identities.selectIdentityForUpdate(any())).thenReturn(owned);
        var relation=new DeviceProjectRelationshipDO();relation.setId(600L);relation.setProjectId(100L);
        when(relations.selectCurrentProject(any())).thenReturn(relation);
        when(shipments.updateById(any(DeviceShipmentDO.class))).thenReturn(1);
        var source=new Shipment("301","TEST-SN","PACK","ITEM","MODEL","NAME",null,
                new ShipmentProductDetails("MODEL","NAME","SECONDARY-MODEL","SECONDARY-NAME",at));
        var input=new Command(1L,1L,100L,"TEST-A",200L,"v4",at,List.of(source),List.of(),List.of());
        assertFalse(service.importEntities(input).getFirst().targets().isEmpty());
        assertEquals(at,historical.getSourceUpdatedAt());
        assertEquals("CURRENT-CONTRACT",owned.getContractNo());assertEquals(999L,owned.getShipmentRecordId());
        verifyNoInteractions(assignments);
        verify(devices,never()).updateById(any(DeviceDO.class));verify(shipments,never()).selectLatest(any());
    }
    @Test void conflictingOriginalShipmentClockDoesNotOverwriteFormalHistory() {
        allowed();var historical=shipment("TEST-A");historical.setSourceUpdatedAt(at.minusDays(1));
        when(shipments.selectBySourceForUpdate(any())).thenReturn(historical);
        var source=new Shipment("301","TEST-SN","PACK","ITEM","MODEL","NAME",null,
                new ShipmentProductDetails("MODEL","NAME","SECONDARY-MODEL","SECONDARY-NAME",at));
        var input=new Command(1L,1L,100L,"TEST-A",200L,"v4",at,List.of(source),List.of(),List.of());
        assertEquals("SHIPMENT_SOURCE_CONTENT_CHANGED",service.importEntities(input).getFirst().reasonCode());
        assertEquals(at.minusDays(1),historical.getSourceUpdatedAt());
        verify(shipments,never()).updateById(any(DeviceShipmentDO.class));
        verifyNoInteractions(identities,assignments,devices);
    }
    @Test void legacyCurrentSoftwareImportCannotChangeTheCurrentNetworkProjection() {
        allowed();var software=new Software("701","TEST-SN","LOG",at,"V1",null,null,null,"B1","C1","P1",true);
        var failure=assertThrows(IllegalArgumentException.class,()->service.importEntities(command(List.of(software))));
        assertEquals("DPPMS_SOFTWARE_REQUIRES_FORMAL_HISTORY_OWNER",failure.getMessage());
        verifyNoInteractions(devices,identities,shipments,relations,assignments,warranties,factories);
    }

}
