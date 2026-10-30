package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceProjectMatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;

import java.util.Set;

public interface DeviceQueryApi {
    /** 数据库按通用产品条件筛选项目设备ID；调用方已校验项目权限，空条件返回空。 */
    java.util.List<Long> findProjectDeviceIdsByProductFilter(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery query);

    /** 当前直接归属项目的全部序列号设备；调用方须已校验项目权限，且处于写事务中。 */
    java.util.List<cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact> lockProjectInstallationDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query);

    DeviceSummaryDTO getDevice(Long deviceId);

    /** 当前主体可见设备的内部预取；缺项不授予权限，消费者仍须按原顺序处理缺失及业务校验。 */
    java.util.List<DeviceSummaryDTO> getVisibleDeviceSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query);

    /** 当前项目设备的完整摘要；复用单设备可见性规则，任一设备不可见仍失败。 */
    java.util.List<DeviceSummaryDTO> getProjectDeviceSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query);

    /** 按设备条件解析当前归属项目ID集合（仅含 project_id 非空的设备）；无命中返回空集合。 */
    Set<Long> resolveProjectIds(DeviceProjectMatchQuery query);
}
