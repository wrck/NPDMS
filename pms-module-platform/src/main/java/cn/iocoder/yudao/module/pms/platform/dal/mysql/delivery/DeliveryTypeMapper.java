package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DeliveryTypeMapper extends BaseMapperX<DeliveryTypeDO> {

    default Optional<DeliveryTypeDO> selectByCode(String typeCode) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<DeliveryTypeDO>()
                .eq(DeliveryTypeDO::getTypeCode, typeCode)));
    }

    DeliveryTypeDO selectCodeForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryTypeCodeLockQuery query);

    default List<DeliveryTypeDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<DeliveryTypeDO>()
                .orderByAsc(DeliveryTypeDO::getTypeCode));
    }
}
