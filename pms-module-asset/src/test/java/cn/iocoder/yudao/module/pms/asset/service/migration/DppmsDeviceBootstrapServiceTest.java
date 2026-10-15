package cn.iocoder.yudao.module.pms.asset.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsDeviceBootstrapServiceTest {
    DeviceBootstrapSourceMapper sources=mock(DeviceBootstrapSourceMapper.class);
    AssetProductOfficialMapper products=mock(AssetProductOfficialMapper.class);
    DeviceMigrationIdentityMapper identities=mock(DeviceMigrationIdentityMapper.class);
    DeviceMapper devices=mock(DeviceMapper.class);DeviceAssignmentMapper relationships=mock(DeviceAssignmentMapper.class);
    DeviceProjectAssignmentService assignments=mock(DeviceProjectAssignmentService.class);
    ProjectDeviceAssignmentGuardApi projectGuard=mock(ProjectDeviceAssignmentGuardApi.class);
    PermissionApi permissions=mock(PermissionApi.class);AdminUserApi users=mock(AdminUserApi.class);
    DppmsDeviceBootstrapService service=new DppmsDeviceBootstrapService(sources,products,identities,devices,relationships,assignments,projectGuard,permissions,users);
    DeviceShipmentDO shipment=new DeviceShipmentDO();
    Seed seed(){return new Seed("SN",10L,"SOURCE-10","C",null,null,null,null,null);}
    BaseCommand command(){return new BaseCommand(1L,2L,"run",LocalDateTime.of(2026,1,1,0,0),List.of(seed()));}
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        shipment.setId(10L);shipment.setTenantId(1L);shipment.setDeviceSn("SN");shipment.setSourceSystem("DPPMS");shipment.setSourceKey("SOURCE-10");shipment.setContractNo("C");
        when(sources.selectLatestShipments(any())).thenReturn(List.of(shipment));when(products.selectShipmentProducts(any())).thenReturn(List.of());
        when(devices.insert(any(DeviceDO.class))).thenReturn(1);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void baseCreationNeedsNoInstallationOrProjectAndPreviewDoesNotWrite() {
        assertEquals("CREATED",service.previewBase(command()).getFirst().action());verify(devices,never()).insert(any(DeviceDO.class));
        assertEquals("CREATED",service.applyBase(command()).getFirst().action());
        var captor=org.mockito.ArgumentCaptor.forClass(DeviceDO.class);verify(devices).insert(captor.capture());
        assertNull(captor.getValue().getProjectId());assertEquals(10L,captor.getValue().getShipmentRecordId());
        verifyNoInteractions(projectGuard,assignments,relationships);
    }
    @Test void deletedOrOtherAuthorityDeviceIsNeverAdoptedAndChangedLatestSourceBlocksCreation() {
        var existing=new DeviceDO();existing.setTenantId(1L);existing.setSn("SN");existing.setSourceSystem("CRM");
        when(identities.selectIdentityForUpdate(any())).thenReturn(existing);
        assertThrows(IllegalArgumentException.class,()->service.applyBase(command()));
        existing.setSourceSystem("DPPMS");existing.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.applyBase(command()));
        when(identities.selectIdentityForUpdate(any())).thenReturn(null);shipment.setId(11L);
        assertThrows(IllegalArgumentException.class,()->service.applyBase(command()));verify(devices,never()).insert(any(DeviceDO.class));
    }
    @Test void tenantAndPermissionChecksPrecedeSourceOrTargetAccess() {
        assertThrows(IllegalArgumentException.class,()->service.applyBase(new BaseCommand(2L,2L,"run",command().sourceReadAt(),List.of(seed()))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.applyBase(new BaseCommand(1L,3L,"run",command().sourceReadAt(),List.of(seed()))));
        verifyNoInteractions(sources,devices,identities);
    }
    Assignment assignment(){return new Assignment(1L,2L,30L,20L,"C",10L,0L,"installation:40",LocalDateTime.of(2026,1,1,0,0));}
    DeviceDO assignableDevice() {
        var device=new DeviceDO();device.setId(20L);device.setTenantId(1L);device.setSn("SN");device.setSourceSystem("DPPMS");
        device.setContractNo("C");device.setProjectAssignmentVersion(0L);
        when(relationships.selectDeviceForUpdate(any())).thenReturn(device);
        when(projectGuard.validate(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult(30L,1L,null,30L,1L,true,null));
        return device;
    }
    @Test void assignmentPreviewDoesNotWriteAndApplyUsesFormalAssignmentWithStableIdentity() {
        assignableDevice();assertEquals("UPDATED",service.previewAssignment(assignment()).action());verifyNoInteractions(assignments);
        service.applyAssignment(assignment());service.applyAssignment(assignment());
        var captured=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand.class);
        verify(assignments,times(2)).assign(captured.capture());
        assertEquals(captured.getAllValues().getFirst(),captured.getAllValues().getLast());
        verify(devices,never()).updateById(any(DeviceDO.class));
    }
    @Test void assignmentRejectsLaterShipmentOtherProjectAndBrokenRelationBeforeWriting() {
        var device=assignableDevice();shipment.setId(11L);
        assertThrows(IllegalArgumentException.class,()->service.applyAssignment(assignment()));
        shipment.setId(10L);device.setProjectId(31L);
        assertThrows(IllegalArgumentException.class,()->service.applyAssignment(assignment()));
        device.setProjectId(30L);
        assertThrows(IllegalArgumentException.class,()->service.applyAssignment(assignment()));
        device.setProjectId(null);device.setProjectAssignmentVersion(1L);
        assertThrows(IllegalArgumentException.class,()->service.applyAssignment(assignment()));
        verifyNoInteractions(assignments);
    }
    @Test void existingMatchingAssignmentIsUnchangedAndFormalOwnerFailureIsPropagated() {
        var device=assignableDevice();device.setProjectId(30L);
        var relation=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO();relation.setProjectId(30L);
        when(relationships.selectCurrentProject(any())).thenReturn(relation);
        assertEquals("UNCHANGED",service.applyAssignment(assignment()).action());verifyNoInteractions(assignments);
        device.setProjectId(null);when(relationships.selectCurrentProject(any())).thenReturn(null);
        when(assignments.assign(any())).thenThrow(new IllegalStateException("formal owner failure"));
        assertEquals("formal owner failure",assertThrows(IllegalStateException.class,()->service.applyAssignment(assignment())).getMessage());
    }
}
