package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceNetworkVersionMapper extends BaseMapperX<DeviceNetworkVersionDO> {

    default DeviceNetworkVersionDO selectByTenantAndSn(Long tenantId, String deviceSn) {
        return selectOne(new LambdaQueryWrapperX<DeviceNetworkVersionDO>()
                .eq(DeviceNetworkVersionDO::getTenantId, tenantId)
                .eq(DeviceNetworkVersionDO::getDeviceSn, deviceSn));
    }
}
