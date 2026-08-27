package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.DeviceShipmentSourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceShipmentMapper extends BaseMapperX<DeviceShipmentDO> {

    default boolean existsBySource(DeviceShipmentSourceQuery query) {
        return selectCount(new LambdaQueryWrapperX<DeviceShipmentDO>()
                .eq(DeviceShipmentDO::getTenantId, query.tenantId())
                .eq(DeviceShipmentDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceShipmentDO::getSourceKey, query.sourceKey())) > 0;
    }

    DeviceShipmentDO selectLatest(@Param("query") LatestDeviceShipmentQuery query);
}
