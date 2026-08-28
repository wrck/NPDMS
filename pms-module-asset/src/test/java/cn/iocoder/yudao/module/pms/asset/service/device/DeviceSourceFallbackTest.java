package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceDetailRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.domain.version.NetworkSoftwareVersion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceSourceFallbackTest {

    @Test
    void shouldKeepLastSuccessfulNetworkVersionWhenCurrentSyncFailed() {
        DeviceQueryApi deviceQueryApi = mock(DeviceQueryApi.class);
        DeviceNetworkVersionMapper versionMapper = mock(DeviceNetworkVersionMapper.class);
        DeviceDetailService service = new DeviceDetailService(deviceQueryApi, versionMapper);
        when(deviceQueryApi.getDevice(8L)).thenReturn(summary());
        DeviceNetworkVersionDO version = new DeviceNetworkVersionDO();
        version.setDeviceSn("SN-8");
        version.setConpVersion("V3.2.1");
        version.setConpType("CONP");
        version.setConpSeries("S3");
        version.setConpMark("3.2.1");
        version.setBootVersion("B1");
        version.setSourceSystem("ITR");
        version.setSourceKey("source-device-8");
        version.setSourceVersion("2");
        version.setSourceUpdatedAt(LocalDateTime.of(2026, 8, 26, 20, 0));
        version.setSyncedAt(LocalDateTime.of(2026, 8, 26, 20, 5));
        version.setSyncStatus("FAILED");
        when(versionMapper.selectByTenantAndSn(1L, "SN-8")).thenReturn(version);

        DeviceDetailRespVO detail = service.getDetail(8L);

        assertEquals("FAILED", detail.networkVersion().syncStatus());
        assertEquals(LocalDateTime.of(2026, 8, 26, 20, 5), detail.networkVersion().syncedAt());
        NetworkSoftwareVersion data = assertInstanceOf(
                NetworkSoftwareVersion.class, detail.networkVersion().data());
        assertEquals("V3.2.1", data.conpVersion());
    }

    @Test
    void shouldSerializeNetworkVersionFieldsForDeviceDetailResponse() {
        NetworkSoftwareVersion version = new NetworkSoftwareVersion(
                "V3.2.1", "CONP", "S3", "3.2.1",
                "B1", "C1", "P1", false,
                "ITR", "key", "1", null, null, "FRESH", null);

        String json = JsonUtils.toJsonString(version);

        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"conpVersion\":\"V3.2.1\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"conpType\":\"CONP\""));
    }

    private DeviceSummaryDTO summary() {
        return new DeviceSummaryDTO(
                8L, 1L, "SN-8", null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null,
                "V3.2.1", "CONP", "S3", "3.2.1");
    }
}
