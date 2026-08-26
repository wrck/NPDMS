package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerQueryApiImpl implements CustomerQueryApi {

    private final CustomerMasterMapper customerMasterMapper;

    @Override
    public CustomerSummaryDTO getCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return toSummary(customerMasterMapper.selectById(customerId));
    }

    @Override
    public List<CustomerSummaryDTO> getCustomers(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return customerMasterMapper.selectByIds(customerIds).stream()
                .map(this::toSummary)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private CustomerSummaryDTO toSummary(CustomerMasterDO customer) {
        if (customer == null || CustomerLifecycleStatus.DELETED.name().equals(customer.getLifecycleStatus())) {
            return null;
        }
        return new CustomerSummaryDTO(customer.getId(), customer.getTenantId(), customer.getCode(), customer.getName(),
                customer.getShortName(), customer.getLifecycleStatus(), customer.getSourceType(),
                customer.getVersion() == null ? null : customer.getVersion().longValue(), customer.getDataAsOf());
    }
}
