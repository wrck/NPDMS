package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;

public interface DeviceQueryApi {

    DeviceSummaryDTO getDevice(Long deviceId);
}
