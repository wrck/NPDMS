package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerDetailQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerPageQuery;
import cn.iocoder.yudao.module.pms.customer.service.customer.CustomerLifecycleUpdate;
import cn.iocoder.yudao.module.pms.customer.service.customer.CustomerPlatformUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerMasterMapper extends BaseMapperX<CustomerMasterDO> {

    default CustomerMasterDO selectByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(new LambdaQueryWrapperX<CustomerMasterDO>()
                .eq(CustomerMasterDO::getTenantId, tenantId)
                .eq(CustomerMasterDO::getCode, code));
    }

    int updatePlatformFieldsByVersion(@Param("query") CustomerPlatformUpdate query);

    CustomerMasterDO selectIncludingDeleted(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int updateLifecycleByVersion(@Param("query") CustomerLifecycleUpdate query);

    CustomerMasterDO selectVisibleById(@Param("query") VisibleCustomerDetailQuery query);

    long selectVisibleCount(@Param("query") VisibleCustomerPageQuery query);

    java.util.List<CustomerMasterDO> selectVisibleList(@Param("query") VisibleCustomerPageQuery query);

    default PageResult<CustomerMasterDO> selectVisiblePage(VisibleCustomerPageQuery query) {
        if (!query.allScope() && (query.scopeSlices() == null || query.scopeSlices().isEmpty())) {
            return PageResult.empty();
        }
        long total = selectVisibleCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectVisibleList(query), total);
    }
}
