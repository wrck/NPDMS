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

    default DeviceShipmentDO selectBySource(DeviceShipmentSourceQuery query) {
        return selectOne(new LambdaQueryWrapperX<DeviceShipmentDO>()
                .eq(DeviceShipmentDO::getTenantId, query.tenantId())
                .eq(DeviceShipmentDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceShipmentDO::getSourceKey, query.sourceKey()));
    }

    default DeviceShipmentDO selectBySourceForUpdate(DeviceShipmentSourceQuery query) {
        return selectOneForUpdate(new LambdaQueryWrapperX<DeviceShipmentDO>().eq(DeviceShipmentDO::getTenantId,query.tenantId())
                .eq(DeviceShipmentDO::getSourceSystem,query.sourceSystem()).eq(DeviceShipmentDO::getSourceKey,query.sourceKey()));
    }
    default boolean existsBySource(DeviceShipmentSourceQuery query) {
        return selectCount(new LambdaQueryWrapperX<DeviceShipmentDO>()
                .eq(DeviceShipmentDO::getTenantId, query.tenantId())
                .eq(DeviceShipmentDO::getSourceSystem, query.sourceSystem())
                .eq(DeviceShipmentDO::getSourceKey, query.sourceKey())) > 0;
    }

    default cn.iocoder.yudao.framework.common.pojo.PageResult<DeviceShipmentDO> selectPage(
            cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.DeviceShipmentPageQuery query) {
        var page=new cn.iocoder.yudao.framework.common.pojo.PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<DeviceShipmentDO>().eq(DeviceShipmentDO::getTenantId,query.tenantId())
                .eq(DeviceShipmentDO::getDeviceSn,query.deviceSn()).orderByDesc(DeviceShipmentDO::getShipmentTime).orderByDesc(DeviceShipmentDO::getId));
    }
    DeviceShipmentDO selectLatest(@Param("query") LatestDeviceShipmentQuery query);
}
