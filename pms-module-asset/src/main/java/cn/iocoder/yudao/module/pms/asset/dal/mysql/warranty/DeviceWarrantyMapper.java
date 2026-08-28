package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceWarrantyMapper extends BaseMapperX<DeviceWarrantyDO> {

    default DeviceWarrantyDO selectByTenantAndDeviceSn(Long tenantId, String deviceSn) {
        return selectOne(new LambdaQueryWrapperX<DeviceWarrantyDO>()
                .eq(DeviceWarrantyDO::getTenantId, tenantId)
                .eq(DeviceWarrantyDO::getDeviceSn, deviceSn));
    }
}
