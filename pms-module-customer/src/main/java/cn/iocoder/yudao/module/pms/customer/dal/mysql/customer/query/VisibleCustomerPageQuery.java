package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeSlice;

import java.util.List;

public record VisibleCustomerPageQuery(
        Long tenantId,
        String code,
        String name,
        String departmentCode,
        String marketCode,
        String systemCode,
        String expendCode,
        String industryCode,
        String lifecycleStatus,
        String sourceType,
        boolean allScope,
        List<CustomerScopeSlice> scopeSlices,
        PageParam pageParam) {
}
