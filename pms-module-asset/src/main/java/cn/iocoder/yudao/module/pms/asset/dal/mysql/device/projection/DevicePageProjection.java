package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection;

/** 统计行保留总数；空页时device为null，不向界面生成虚假设备。 */
@lombok.Data
public class DevicePageProjection {
    private Long deviceId;
    private Long total;
    private DeviceListProjection device;
}
