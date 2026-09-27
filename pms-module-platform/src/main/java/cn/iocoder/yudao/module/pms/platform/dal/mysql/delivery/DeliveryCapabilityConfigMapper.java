package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryCapabilityConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeliveryCapabilityConfigMapper extends BaseMapperX<DeliveryCapabilityConfigDO> {

    default List<DeliveryCapabilityConfigDO> selectByEntity(String ownerModule, String entityType) {
        return selectList(new LambdaQueryWrapperX<DeliveryCapabilityConfigDO>()
                .eq(DeliveryCapabilityConfigDO::getOwnerModule, ownerModule)
                .eq(DeliveryCapabilityConfigDO::getEntityType, entityType)
                .orderByAsc(DeliveryCapabilityConfigDO::getTypeCode));
    }
}
