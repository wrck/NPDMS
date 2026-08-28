package cn.iocoder.yudao.module.pms.customer.service.security;

import java.util.Set;

public record CustomerScopeRequest(
        Long tenantId,
        Long userId,
        Set<Long> roleIds,
        Set<String> roleCodes,
        boolean integrationIdentity) {
}
