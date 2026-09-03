package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

import java.time.LocalDateTime;

public record ProductTypeSourceMappingConflictUpdate(
        Long tenantId,
        Long mappingId,
        String productTypeCode,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        String payloadHash) {
}
