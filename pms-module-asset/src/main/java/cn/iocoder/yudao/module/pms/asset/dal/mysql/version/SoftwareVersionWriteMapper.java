package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.SoftwareVersionDeviceLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SoftwareVersionWriteMapper {
    DeviceDO selectDeviceForUpdate(@Param("query") SoftwareVersionDeviceLockQuery query);
    DeviceNetworkVersionDO selectCurrentForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("deviceSn") String deviceSn);
    int replaceCurrent(@Param("row") DeviceNetworkVersionDO row);
    int updateDeviceProjection(@Param("row") DeviceDO row);
}
