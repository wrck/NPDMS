package cn.iocoder.yudao.module.pms.customer.api.query;

import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;

import java.util.Collection;
import java.util.List;

public interface CustomerQueryApi {

    CustomerSummaryDTO getCustomer(Long customerId);

    List<CustomerSummaryDTO> getCustomers(Collection<Long> customerIds);
}
