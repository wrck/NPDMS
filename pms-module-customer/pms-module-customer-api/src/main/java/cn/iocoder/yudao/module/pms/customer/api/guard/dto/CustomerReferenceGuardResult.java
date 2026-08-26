package cn.iocoder.yudao.module.pms.customer.api.guard.dto;

import java.time.LocalDateTime;

public record CustomerReferenceGuardResult(
        String status,
        String provider,
        long referenceCount,
        LocalDateTime dataAsOf) {
}
