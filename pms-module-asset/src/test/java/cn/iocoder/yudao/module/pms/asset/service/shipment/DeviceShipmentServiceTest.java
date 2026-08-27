package cn.iocoder.yudao.module.pms.asset.service.shipment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.DeviceShipmentSourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import cn.iocoder.yudao.module.pms.asset.service.shipment.command.ApplyDeviceShipmentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceShipmentServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceShipmentMapper shipmentMapper;
    private DeviceShipmentService service;

    @BeforeEach
    void setUp() {
        service = new DeviceShipmentService(deviceMapper, shipmentMapper);
    }

    @Test
    void shouldProjectFourFieldsFromSameLatestRecord() {
        DeviceDO device = device();
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device);
        doAnswer(invocation -> {
            DeviceShipmentDO shipment = invocation.getArgument(0);
            shipment.setId(12L);
            return 1;
        }).when(shipmentMapper).insert(any(DeviceShipmentDO.class));
        when(shipmentMapper.selectLatest(any(LatestDeviceShipmentQuery.class)))
                .thenAnswer(invocation -> shipment(12L, LocalDateTime.of(2026, 8, 26, 20, 0), "PK-12", "CT-12"));

        service.apply(command("shipment-12", LocalDateTime.of(2026, 8, 26, 20, 0), "PK-12", "CT-12"));

        verify(deviceMapper).updateById(argThat((DeviceDO update) ->
                Long.valueOf(12L).equals(update.getShipmentRecordId())
                        && LocalDateTime.of(2026, 8, 26, 20, 0).equals(update.getShipmentTime())
                        && "PK-12".equals(update.getPackageNo())
                        && "CT-12".equals(update.getContractNo())));
    }

    @Test
    void shouldKeepProjectionWhenLateRecordIsNotLatest() {
        when(deviceMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(device());
        doAnswer(invocation -> {
            DeviceShipmentDO shipment = invocation.getArgument(0);
            shipment.setId(11L);
            return 1;
        }).when(shipmentMapper).insert(any(DeviceShipmentDO.class));
        when(shipmentMapper.selectLatest(any(LatestDeviceShipmentQuery.class)))
                .thenReturn(shipment(12L, LocalDateTime.of(2026, 8, 26, 20, 0), "PK-12", "CT-12"));

        service.apply(command("shipment-11", LocalDateTime.of(2026, 8, 26, 19, 0), "PK-11", "CT-11"));

        verify(deviceMapper, never()).updateById(any(DeviceDO.class));
    }

    @Test
    void shouldIgnoreDuplicateSourceRecord() {
        when(shipmentMapper.existsBySource(any(DeviceShipmentSourceQuery.class))).thenReturn(true);

        service.apply(command("shipment-12", LocalDateTime.of(2026, 8, 26, 20, 0), "PK-12", "CT-12"));

        verifyNoInteractions(deviceMapper);
        verify(shipmentMapper, never()).insert(any(DeviceShipmentDO.class));
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        return device;
    }

    private DeviceShipmentDO shipment(Long id, LocalDateTime shipmentTime, String packageNo, String contractNo) {
        DeviceShipmentDO shipment = new DeviceShipmentDO();
        shipment.setId(id);
        shipment.setShipmentTime(shipmentTime);
        shipment.setPackageNo(packageNo);
        shipment.setContractNo(contractNo);
        return shipment;
    }

    private ApplyDeviceShipmentCommand command(String sourceKey, LocalDateTime shipmentTime,
                                                String packageNo, String contractNo) {
        return new ApplyDeviceShipmentCommand(
                1L, "SN-8", shipmentTime, packageNo, contractNo, "SHIPMENT",
                "MES", sourceKey, "2", shipmentTime,
                LocalDateTime.of(2026, 8, 26, 20, 5), "FRESH");
    }
}
