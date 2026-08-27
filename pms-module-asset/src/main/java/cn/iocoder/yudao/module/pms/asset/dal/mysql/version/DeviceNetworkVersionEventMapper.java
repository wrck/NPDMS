package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionEventDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceNetworkVersionSourceQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceNetworkVersionEventMapper extends BaseMapperX<DeviceNetworkVersionEventDO> {

    default boolean existsBySource(DeviceNetworkVersionSourceQuery query) {
        return selectCount(new LambdaQueryWrapperX<DeviceNetworkVersionEventDO>()
                .eq(DeviceNetworkVersionEventDO::getTenantId, query.tenantId())
                .eq(DeviceNetworkVersionEventDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceNetworkVersionEventDO::getSourceDeviceKey, query.sourceDeviceKey())
                .eq(DeviceNetworkVersionEventDO::getSourceEventKey, query.sourceEventKey())) > 0;
    }
}
