package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionEventDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceNetworkVersionSourceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceNetworkVersionEventMapper extends BaseMapperX<DeviceNetworkVersionEventDO> {
    cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareLegacyEventWithdrawalDO selectLegacyWithdrawalForUpdate(
            @Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareLegacyEventWithdrawalQuery query);
    int withdrawLegacyEvent(@Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareLegacyEventWithdrawalQuery query);
    DeviceNetworkVersionEventDO selectSourceIdentity(@Param("query") DeviceNetworkVersionSourceQuery query);

    default DeviceNetworkVersionEventDO selectBySource(DeviceNetworkVersionSourceQuery query) {
        return selectOne(new LambdaQueryWrapperX<DeviceNetworkVersionEventDO>()
                .eq(DeviceNetworkVersionEventDO::getTenantId, query.tenantId())
                .eq(DeviceNetworkVersionEventDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceNetworkVersionEventDO::getSourceDeviceKey, query.sourceDeviceKey())
                .eq(DeviceNetworkVersionEventDO::getSourceEventKey, query.sourceEventKey()));
    }

    default boolean existsBySource(DeviceNetworkVersionSourceQuery query) {
        return selectCount(new LambdaQueryWrapperX<DeviceNetworkVersionEventDO>()
                .eq(DeviceNetworkVersionEventDO::getTenantId, query.tenantId())
                .eq(DeviceNetworkVersionEventDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceNetworkVersionEventDO::getSourceDeviceKey, query.sourceDeviceKey())
                .eq(DeviceNetworkVersionEventDO::getSourceEventKey, query.sourceEventKey())) > 0;
    }
}
