package cn.iocoder.yudao.module.pms.customer.service.security;

import java.util.Set;

public record CustomerScopeSlice(
        Set<String> departmentCodes,
        Set<String> marketCodes,
        Set<String> systemCodes,
        Set<String> expendCodes,
        Set<String> industryCodes) {
}
