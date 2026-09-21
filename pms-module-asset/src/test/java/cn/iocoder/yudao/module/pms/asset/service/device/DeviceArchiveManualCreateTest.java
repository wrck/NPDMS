package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceVersionMapper;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceArchiveManualCreateTest {
    private final DeviceMapper devices = mock(DeviceMapper.class);
    private final DeviceVersionMapper versions = mock(DeviceVersionMapper.class);
    private final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    private final DeviceArchiveServiceImpl service = new DeviceArchiveServiceImpl(devices, versions, customers);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void manualCreateKeepsPlatformProvenanceAndCreationEvidence() {
        var request = request();
        when(versions.selectMaxVersionNo(anyLong())).thenReturn(0);
        Long id = service.createDevice(request);
        var device = ArgumentCaptor.forClass(DeviceDO.class);
        verify(devices).insert(device.capture());
        assertNotNull(id);
        assertTrue(id > 0);
        assertEquals(id, device.getValue().getId());
        assertEquals("PLATFORM_MANUAL", device.getValue().getSourceSystem());
        assertEquals("PENDING_RECONCILIATION", device.getValue().getSyncStatus());
        assertNull(device.getValue().getSourceKey());
        assertNull(device.getValue().getSourceVersion());
        var history = ArgumentCaptor.forClass(DeviceVersionDO.class);
        verify(versions).insert(history.capture());
        assertEquals(id, history.getValue().getDeviceId());
        assertEquals(1, history.getValue().getVersionNo());
        assertEquals("CREATE", history.getValue().getChangeType());
        assertTrue(history.getValue().getChangeDescription().contains(request.getManualReason()));
        assertTrue(history.getValue().getChangeDescription().contains(request.getManualEvidence()));
        assertTrue(history.getValue().getAfterSnapshot().contains("PLATFORM_MANUAL"));
    }

    @Test void missingReasonOrEvidenceCannotWriteDeviceOrHistory() {
        var noReason = request(); noReason.setManualReason(" ");
        var noEvidence = request(); noEvidence.setManualEvidence(null);
        assertThrows(ServiceException.class, () -> service.createDevice(noReason));
        assertThrows(ServiceException.class, () -> service.createDevice(noEvidence));
        verifyNoInteractions(devices, versions, customers);
    }

    @Test void duplicateSerialNumberCannotCreateAnotherManualIdentity() {
        var existing = new DeviceDO(); existing.setId(9L); existing.setSn("MANUAL-SN");
        when(devices.selectByTenantAndSn(1L, "MANUAL-SN")).thenReturn(existing);
        assertThrows(ServiceException.class, () -> service.createDevice(request()));
        verify(devices, never()).insert(any(DeviceDO.class));
        verifyNoInteractions(versions);
    }

    private DeviceArchiveSaveReqVO request() {
        var request = new DeviceArchiveSaveReqVO();
        request.setSn("MANUAL-SN"); request.setName("Manual device");
        request.setManualReason("Isolated business acceptance");
        request.setManualEvidence("Dedicated acceptance fixture; no external MES identity");
        return request;
    }
}
