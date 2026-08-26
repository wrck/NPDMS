package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query;

import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeSlice;

import java.util.List;

public record VisibleCustomerDetailQuery(
        Long tenantId,
        Long customerId,
        boolean allScope,
        List<CustomerScopeSlice> scopeSlices) {
}
