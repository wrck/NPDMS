package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerContactMasterMapper extends BaseMapperX<CustomerContactMasterDO> {
    default PageResult<CustomerContactMasterDO> selectPage(ContactMasterPageQuery query) {
        return selectPage(query.page(), new LambdaQueryWrapperX<CustomerContactMasterDO>()
                .eq(CustomerContactMasterDO::getTenantId, query.tenantId())
                .eq(CustomerContactMasterDO::getCustomerId, query.customerId())
                .likeIfPresent(CustomerContactMasterDO::getName, query.name())
                .eqIfPresent(CustomerContactMasterDO::getStatus, query.status())
                .orderByDesc(CustomerContactMasterDO::getId));
    }

    default CustomerContactMasterDO selectByRow(ContactMasterRowQuery query) {
        return selectOne(new LambdaQueryWrapperX<CustomerContactMasterDO>()
                .eq(CustomerContactMasterDO::getTenantId, query.tenantId())
                .eq(CustomerContactMasterDO::getCustomerId, query.customerId())
                .eq(CustomerContactMasterDO::getId, query.contactId()));
    }

    default long countCustomerReferences(ContactCustomerReferenceQuery query) {
        return selectCount(new LambdaQueryWrapperX<CustomerContactMasterDO>()
                .eq(CustomerContactMasterDO::getTenantId, query.tenantId())
                .eq(CustomerContactMasterDO::getCustomerId, query.customerId()));
    }

    CustomerContactMasterDO selectActivePrimaryForUpdate(@Param("query") ContactCustomerReferenceQuery query);
    cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO selectCustomerForUpdate(
            @Param("query") ContactCustomerReferenceQuery query);

    int deleteUnreferenced(@Param("query") ContactMasterDeleteCommand query);
    CustomerContactMasterDO selectForUpdate(@Param("query") ContactMasterRowQuery query);
    long selectVisibleCount(@Param("query") VisibleContactPageQuery query);
    java.util.List<CustomerContactMasterDO> selectVisiblePage(@Param("query") VisibleContactPageQuery query);
    long selectAvailableSourceCount(@Param("query") AvailableContactSourceQuery query);
    java.util.List<CustomerContactMasterDO> selectAvailableSourcePage(@Param("query") AvailableContactSourceQuery query);
}
