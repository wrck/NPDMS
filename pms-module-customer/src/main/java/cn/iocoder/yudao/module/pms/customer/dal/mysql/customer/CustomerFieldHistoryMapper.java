package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerFieldHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CustomerHistoryListQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomerFieldHistoryMapper extends BaseMapperX<CustomerFieldHistoryDO> {

    default List<CustomerFieldHistoryDO> selectListByCustomer(CustomerHistoryListQuery query) {
        return selectList(new LambdaQueryWrapperX<CustomerFieldHistoryDO>()
                .eq(CustomerFieldHistoryDO::getTenantId, query.tenantId())
                .eq(CustomerFieldHistoryDO::getCustomerId, query.customerId())
                .orderByDesc(CustomerFieldHistoryDO::getOccurredAt)
                .orderByDesc(CustomerFieldHistoryDO::getId));
    }
}
