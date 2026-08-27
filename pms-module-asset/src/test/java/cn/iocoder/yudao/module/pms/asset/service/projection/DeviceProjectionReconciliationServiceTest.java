package cn.iocoder.yudao.module.pms.asset.service.projection;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.DeviceLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.CurrentDeviceLocationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceProjectionReconciliationServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceShipmentMapper shipmentMapper;
    @Mock private DeviceAssignmentMapper assignmentMapper;
    @Mock private DeviceLocationMapper locationMapper;
    @Mock private DeviceWarrantyMapper warrantyMapper;
    @Mock private DeviceNetworkVersionMapper versionMapper;
    @Mock private OperationAuditApi auditApi;

    private DeviceProjectionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceProjectionReconciliationService(
                deviceMapper, shipmentMapper, assignmentMapper, locationMapper,
                warrantyMapper, versionMapper, auditApi);
    }

    @Test
    void shouldDetectAndRebuildAllSixProjectionTypesFromCompleteFacts() {
        DeviceDO device = device();
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);
        when(shipmentMapper.selectLatest(new LatestDeviceShipmentQuery(1L, "SN-8"))).thenReturn(shipment());
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(project());
        when(assignmentMapper.selectCurrentCustomer(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(customer());
        when(locationMapper.selectCurrent(new CurrentDeviceLocationQuery(1L, "SN-8"))).thenReturn(location());
        when(warrantyMapper.selectByTenantAndDeviceSn(1L, "SN-8")).thenReturn(warranty());
        when(versionMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(version());

        DeviceProjectionReconciliationResult result = service.rebuild(command());

        assertTrue(result.rebuilt());
        assertEquals(6, result.driftTypes().size());
        assertTrue(result.missingSourceTypes().isEmpty());
        ArgumentCaptor<DeviceDO> updateCaptor = ArgumentCaptor.forClass(DeviceDO.class);
        verify(deviceMapper).updateById(updateCaptor.capture());
        DeviceDO update = updateCaptor.getValue();
        assertEquals(81L, update.getShipmentRecordId());
        assertEquals(101L, update.getProjectId());
        assertEquals(201L, update.getCustomerId());
        assertEquals(301L, update.getLocationRecordId());
        assertEquals("ACTIVE", update.getWarrantyStatus());
        assertEquals("V3.2.1", update.getConpVersion());
        verify(auditApi).record(eq(1L), eq(9L), eq("corr-8"), eq("DEVICE_PROJECTION_REBUILD"),
                eq("Device"), eq("SN-8"), eq("SUCCESS"), any(Map.class));
    }

    @Test
    void shouldProtectExistingProjectionWhenSourceIsMissingAndAuditException() {
        DeviceDO device = projectedDevice();
        device.setShipmentRecordId(70L);
        device.setShipmentTime(LocalDateTime.of(2026, 8, 20, 10, 0));
        device.setPackageNo("PK-OLD");
        device.setContractNo("CT-OLD");
        device.setProjectId(100L);
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(project());
        when(assignmentMapper.selectCurrentCustomer(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(customer());
        when(locationMapper.selectCurrent(new CurrentDeviceLocationQuery(1L, "SN-8"))).thenReturn(location());
        when(warrantyMapper.selectByTenantAndDeviceSn(1L, "SN-8")).thenReturn(warranty());
        when(versionMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(version());

        DeviceProjectionReconciliationResult result = service.rebuild(command());

        assertTrue(result.rebuilt());
        assertEquals(java.util.List.of(DeviceProjectionType.PROJECT), result.driftTypes());
        assertEquals(java.util.List.of(DeviceProjectionType.SHIPMENT), result.missingSourceTypes());
        verify(deviceMapper).updateById(argThat((DeviceDO update) ->
                Long.valueOf(101L).equals(update.getProjectId())
                        && update.getShipmentRecordId() == null
                        && update.getShipmentTime() == null
                        && update.getPackageNo() == null
                        && update.getContractNo() == null));
        verify(auditApi).record(eq(1L), eq(9L), eq("corr-8"), eq("DEVICE_PROJECTION_RECONCILE"),
                eq("Device"), eq("SN-8"), eq("SOURCE_MISSING"), any(Map.class));
    }

    @Test
    void shouldBeIdempotentWhenProjectionAlreadyMatchesSources() {
        DeviceDO device = projectedDevice();
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);
        when(shipmentMapper.selectLatest(new LatestDeviceShipmentQuery(1L, "SN-8"))).thenReturn(shipment());
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(project());
        when(assignmentMapper.selectCurrentCustomer(new DeviceAssignmentLockQuery(1L, 8L))).thenReturn(customer());
        when(locationMapper.selectCurrent(new CurrentDeviceLocationQuery(1L, "SN-8"))).thenReturn(location());
        when(warrantyMapper.selectByTenantAndDeviceSn(1L, "SN-8")).thenReturn(warranty());
        when(versionMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(version());

        DeviceProjectionReconciliationResult result = service.rebuild(command());

        assertFalse(result.rebuilt());
        assertTrue(result.driftTypes().isEmpty());
        assertTrue(result.missingSourceTypes().isEmpty());
        verify(deviceMapper, never()).updateById(any(DeviceDO.class));
        verify(auditApi).record(eq(1L), eq(9L), eq("corr-8"), eq("DEVICE_PROJECTION_RECONCILE"),
                eq("Device"), eq("SN-8"), eq("CONSISTENT"), any(Map.class));
    }

    private DeviceProjectionReconciliationCommand command() {
        return new DeviceProjectionReconciliationCommand(1L, "SN-8", 9L, "corr-8");
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        device.setProjectAssignmentVersion(1L);
        device.setCustomerAssignmentVersion(2L);
        device.setVersion(3);
        return device;
    }

    private DeviceDO projectedDevice() {
        DeviceDO device = device();
        DeviceShipmentDO shipment = shipment();
        device.setShipmentRecordId(shipment.getId());
        device.setShipmentTime(shipment.getShipmentTime());
        device.setPackageNo(shipment.getPackageNo());
        device.setContractNo(shipment.getContractNo());
        device.setProjectId(101L);
        device.setCustomerId(201L);
        DeviceLocationDO location = location();
        device.setLocationRecordId(location.getId());
        device.setSiteId(location.getSiteId());
        device.setSiteLocationId(location.getSiteLocationId());
        device.setLocationResolutionStatus(location.getResolutionStatus());
        device.setLocationSnapshot(location.getLocationSnapshot());
        device.setLocationEffectiveFrom(location.getEffectiveFrom());
        DeviceWarrantyDO warranty = warranty();
        device.setWarrantyStartDate(warranty.getWarrantyStartDate());
        device.setWarrantyEndDate(warranty.getWarrantyEndDate());
        device.setWarrantyStatus(warranty.getWarrantyStatus());
        DeviceNetworkVersionDO version = version();
        device.setConpVersion(version.getConpVersion());
        device.setConpType(version.getConpType());
        device.setConpSeries(version.getConpSeries());
        device.setConpMark(version.getConpMark());
        return device;
    }

    private DeviceShipmentDO shipment() {
        DeviceShipmentDO shipment = new DeviceShipmentDO();
        shipment.setId(81L);
        shipment.setShipmentTime(LocalDateTime.of(2026, 8, 26, 20, 0));
        shipment.setPackageNo("PK-81");
        shipment.setContractNo("CT-81");
        return shipment;
    }

    private DeviceProjectRelationshipDO project() {
        DeviceProjectRelationshipDO relationship = new DeviceProjectRelationshipDO();
        relationship.setProjectId(101L);
        relationship.setAssignmentVersion(1L);
        return relationship;
    }

    private DeviceCustomerRelationshipDO customer() {
        DeviceCustomerRelationshipDO relationship = new DeviceCustomerRelationshipDO();
        relationship.setCustomerId(201L);
        relationship.setAssignmentVersion(2L);
        return relationship;
    }

    private DeviceLocationDO location() {
        DeviceLocationDO location = new DeviceLocationDO();
        location.setId(301L);
        location.setSiteId(31L);
        location.setSiteLocationId(32L);
        location.setResolutionStatus("RESOLVED");
        location.setLocationSnapshot("A机房/A01机柜/U08");
        location.setEffectiveFrom(LocalDateTime.of(2026, 8, 25, 10, 0));
        return location;
    }

    private DeviceWarrantyDO warranty() {
        DeviceWarrantyDO warranty = new DeviceWarrantyDO();
        warranty.setWarrantyStartDate(LocalDate.of(2026, 1, 1));
        warranty.setWarrantyEndDate(LocalDate.of(2028, 1, 1));
        warranty.setWarrantyStatus("ACTIVE");
        return warranty;
    }

    private DeviceNetworkVersionDO version() {
        DeviceNetworkVersionDO version = new DeviceNetworkVersionDO();
        version.setConpVersion("V3.2.1");
        version.setConpType("CONP");
        version.setConpSeries("S3");
        version.setConpMark("3.2.1");
        return version;
    }
}
