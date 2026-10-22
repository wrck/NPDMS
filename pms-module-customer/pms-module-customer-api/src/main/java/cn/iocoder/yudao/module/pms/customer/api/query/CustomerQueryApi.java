package cn.iocoder.yudao.module.pms.customer.api.query;

import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;

import java.util.Collection;
import java.util.List;

public interface CustomerQueryApi {
    /** 内部筛选候选ID；不授予客户或设备读取权限。 */
    record NameQuery(Long tenantId, String keyword) {}
    java.util.Set<Long> findCustomerIdsByName(NameQuery query);

    CustomerSummaryDTO getCustomer(Long customerId);

    /** 按租户内稳定编码精确查询，并重验当前用户的客户查询权限与数据范围。 */
    CustomerSummaryDTO getCustomerByCode(CustomerCodeQuery query);

    /** 绑定事务内按编码回源并锁定主档；与停用/删除串行，权限和数据范围不变。 */
    CustomerSummaryDTO lockCustomerByCode(CustomerCodeQuery query);

    List<CustomerSummaryDTO> getCustomers(Collection<Long> customerIds);
}
