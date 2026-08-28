package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerExternalMappingDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CurrentCustomerMappingQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerExternalMappingMapper extends BaseMapperX<CustomerExternalMappingDO> {

    default CustomerExternalMappingDO selectCurrent(CurrentCustomerMappingQuery query) {
        return selectOne(new LambdaQueryWrapperX<CustomerExternalMappingDO>()
                .eq(CustomerExternalMappingDO::getTenantId, query.tenantId())
                .eqIfPresent(CustomerExternalMappingDO::getSourceSystem, query.sourceSystem())
                .eqIfPresent(CustomerExternalMappingDO::getSourceKey, query.sourceKey())
                .eqIfPresent(CustomerExternalMappingDO::getCustomerId, query.customerId())
                .isNull(CustomerExternalMappingDO::getEffectiveTo));
    }

    default CustomerExternalMappingDO selectCurrentByCustomerId(Long tenantId, Long customerId) {
        return selectCurrent(CurrentCustomerMappingQuery.byCustomer(tenantId, customerId));
    }
}
