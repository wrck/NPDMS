package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceLogVersionProjectionMapper {
    int replaceCurrent(DeviceNetworkVersionDO version);
    int updateDeviceVersion(DeviceDO device);
}
