package cn.iocoder.yudao.module.pms.customer.service.security;

import java.time.LocalDateTime;
import java.util.Set;

public record CustomerScopeSliceQuery(
        Long tenantId,
        Long userId,
        Set<Long> roleIds,
        LocalDateTime effectiveAt) {
}
