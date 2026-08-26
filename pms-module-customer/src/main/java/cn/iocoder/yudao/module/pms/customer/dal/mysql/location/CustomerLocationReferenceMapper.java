package cn.iocoder.yudao.module.pms.customer.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.query.CurrentCustomerLocationListQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.query.CurrentCustomerLocationQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerLocationReferenceMapper extends BaseMapperX<CustomerLocationReferenceDO> {

    default CustomerLocationReferenceDO selectCurrent(CurrentCustomerLocationQuery query) {
        return selectOne(new LambdaQueryWrapperX<CustomerLocationReferenceDO>()
                .eq(CustomerLocationReferenceDO::getTenantId, query.tenantId())
                .eq(CustomerLocationReferenceDO::getCustomerId, query.customerId())
                .eq(CustomerLocationReferenceDO::getLocationType, query.locationType())
                .isNull(CustomerLocationReferenceDO::getEffectiveTo));
    }

    default List<CustomerLocationReferenceDO> selectCurrentList(CurrentCustomerLocationListQuery query) {
        return selectList(new LambdaQueryWrapperX<CustomerLocationReferenceDO>()
                .eq(CustomerLocationReferenceDO::getTenantId, query.tenantId())
                .eq(CustomerLocationReferenceDO::getCustomerId, query.customerId())
                .isNull(CustomerLocationReferenceDO::getEffectiveTo)
                .orderByAsc(CustomerLocationReferenceDO::getLocationType)
                .orderByAsc(CustomerLocationReferenceDO::getId));
    }
}
