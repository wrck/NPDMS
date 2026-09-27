package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DeliveryRequirementMapper extends BaseMapperX<DeliveryRequirementDO> {

    default Optional<DeliveryRequirementDO> selectByOwnerAndType(String ownerModule, String entityType,
                                                                 Long entityId, String typeCode) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getOwnerModule, ownerModule)
                .eq(DeliveryRequirementDO::getEntityType, entityType)
                .eq(DeliveryRequirementDO::getEntityId, entityId)
                .eq(DeliveryRequirementDO::getTypeCode, typeCode)));
    }

    default List<DeliveryRequirementDO> selectByEntity(String ownerModule, String entityType, Long entityId) {
        return selectList(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getOwnerModule, ownerModule)
                .eq(DeliveryRequirementDO::getEntityType, entityType)
                .eq(DeliveryRequirementDO::getEntityId, entityId)
                .orderByAsc(DeliveryRequirementDO::getTypeCode));
    }
}
