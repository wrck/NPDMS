package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceProjectMatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;

import java.util.Set;

public interface DeviceQueryApi {

    DeviceSummaryDTO getDevice(Long deviceId);

    /** 按设备条件解析当前归属项目ID集合（仅含 project_id 非空的设备）；无命中返回空集合。 */
    Set<Long> resolveProjectIds(DeviceProjectMatchQuery query);
}
