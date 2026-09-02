package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.DeviceCurrentProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.projection.AuthorizedDeviceProductTypeProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.DeviceCurrentProductTypeClose;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.DeviceCurrentProductTypeLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceCurrentProductTypeMapper
        extends BaseMapperX<DeviceCurrentProductTypeDO> {

    default List<AuthorizedDeviceProductTypeProjection> selectAuthorizedCurrent(
            AuthorizedDeviceProductTypesQuery query) {
        if (query.deviceIds() == null || query.deviceIds().isEmpty()
                || query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty()) {
            return List.of();
        }
        return selectAuthorizedCurrentInternal(query);
    }

    List<AuthorizedDeviceProductTypeProjection> selectAuthorizedCurrentInternal(
            @Param("query") AuthorizedDeviceProductTypesQuery query);

    DeviceCurrentProductTypeDO selectCurrentForUpdate(
            @Param("query") DeviceCurrentProductTypeLockQuery query);

    int closeCurrent(@Param("update") DeviceCurrentProductTypeClose update);
}
