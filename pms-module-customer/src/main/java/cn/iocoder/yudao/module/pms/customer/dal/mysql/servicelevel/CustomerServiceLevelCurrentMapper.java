package cn.iocoder.yudao.module.pms.customer.dal.mysql.servicelevel;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelCurrentApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 当前等级读取独立于原维护 Mapper，保留原维护行为。 */
@Mapper
public interface CustomerServiceLevelCurrentMapper extends BaseMapperX<CustomerServiceLevelDO> {
    default List<CustomerServiceLevelDO> selectCustomerRecords(CustomerServiceLevelCurrentApi.Query query) {
        return selectList(new LambdaQueryWrapperX<CustomerServiceLevelDO>()
                .eq(CustomerServiceLevelDO::getTenantId, query.tenantId())
                .eq(CustomerServiceLevelDO::getCustomerId, query.customerId())
                .orderByAsc(CustomerServiceLevelDO::getId));
    }
}
