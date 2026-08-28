package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.CurrentDeviceLocationQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DeviceLocationMapper extends BaseMapperX<DeviceLocationDO> {

    default DeviceLocationDO selectByTenantAndInstallation(Long tenantId, Long installationId) {
        return selectOne(new LambdaQueryWrapperX<DeviceLocationDO>()
                .eq(DeviceLocationDO::getTenantId, tenantId)
                .eq(DeviceLocationDO::getInstallationId, installationId));
    }

    default DeviceLocationDO selectCurrent(CurrentDeviceLocationQuery query) {
        return selectOne(new LambdaQueryWrapperX<DeviceLocationDO>()
                .eq(DeviceLocationDO::getTenantId, query.tenantId())
                .eq(DeviceLocationDO::getDeviceSn, query.deviceSn())
                .isNull(DeviceLocationDO::getEffectiveTo));
    }

    int closeCurrent(
            @Param("tenantId") Long tenantId,
            @Param("deviceSn") String deviceSn,
            @Param("effectiveAt") LocalDateTime effectiveAt);
}
