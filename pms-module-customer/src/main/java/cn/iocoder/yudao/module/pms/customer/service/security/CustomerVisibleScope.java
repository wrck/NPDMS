package cn.iocoder.yudao.module.pms.customer.service.security;

import java.util.List;

public record CustomerVisibleScope(
        boolean all,
        List<CustomerScopeSlice> slices) {
}
