package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

import java.time.LocalDateTime;

public record ReadinessWaiverFact(
        Long waiverId,
        Long itemId,
        String itemCode,
        Integer waiverNo,
        String statusCode,
        String blockerCodesSnapshot,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer version) {}
