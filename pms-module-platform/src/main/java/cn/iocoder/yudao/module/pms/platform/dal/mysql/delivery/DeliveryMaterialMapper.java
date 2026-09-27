package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DeliveryMaterialMapper extends BaseMapperX<DeliveryMaterialDO> {

    default List<DeliveryMaterialDO> selectByEntity(String ownerModule, String entityType, Long entityId,
                                                    String typeCode) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getOwnerModule, ownerModule)
                .eq(DeliveryMaterialDO::getEntityType, entityType)
                .eq(DeliveryMaterialDO::getEntityId, entityId)
                .eqIfPresent(DeliveryMaterialDO::getTypeCode, typeCode)
                .orderByDesc(DeliveryMaterialDO::getId));
    }

    default List<DeliveryMaterialDO> selectByMaterialIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .in(DeliveryMaterialDO::getId, ids));
    }
}
