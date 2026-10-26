package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceProjectMatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;

import java.util.Set;

public interface DeviceQueryApi {

    /** 当前直接归属项目的全部序列号设备；调用方须已校验项目权限，且处于写事务中。 */
    java.util.List<cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact> lockProjectInstallationDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query);

    DeviceSummaryDTO getDevice(Long deviceId);

    /** 当前归属或项目清单关联设备的完整摘要；复用单设备可见性规则，任一设备不可见仍失败。 */
    java.util.List<DeviceSummaryDTO> getProjectDeviceSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query);

    /** 按设备条件解析当前归属及项目清单关联的项目ID集合；无命中返回空集合。 */
    Set<Long> resolveProjectIds(DeviceProjectMatchQuery query);
}
