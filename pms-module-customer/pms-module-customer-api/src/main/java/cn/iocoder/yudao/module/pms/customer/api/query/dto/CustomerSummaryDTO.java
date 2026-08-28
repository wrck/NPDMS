package cn.iocoder.yudao.module.pms.customer.api.query.dto;

import java.time.LocalDateTime;

public record CustomerSummaryDTO(
        Long id,
        Long tenantId,
        String code,
        String name,
        String shortName,
        String lifecycleStatus,
        String sourceType,
        Long version,
        LocalDateTime dataAsOf) {
}
