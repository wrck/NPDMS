package cn.iocoder.yudao.module.pms.asset.api.producttype.dto;

import java.time.LocalDateTime;

public record ProductTypeCodeResult(
        String productTypeCode,
        boolean exists,
        boolean enabled,
        String displayName,
        String sourceSystem,
        String sourceVersion,
        String syncStatus,
        LocalDateTime lastSuccessfulSyncTime,
        boolean fromLastSuccessfulCopy) {
}
