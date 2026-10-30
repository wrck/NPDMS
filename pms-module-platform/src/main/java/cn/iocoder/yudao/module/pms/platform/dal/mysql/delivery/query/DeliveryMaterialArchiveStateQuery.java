package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

import java.time.LocalDateTime;

/** 归档状态推进场景化查询：仅当行仍为 PENDING_COMPENSATION 时生效。 */
public record DeliveryMaterialArchiveStateQuery(Long tenantId, Long materialId, String archiveStatus,
                                                String failureCode, LocalDateTime archiveTime, String updater) {

    public DeliveryMaterialArchiveStateQuery {
        if (tenantId == null || tenantId < 0 || materialId == null || materialId <= 0
                || archiveStatus == null || archiveStatus.isBlank()) {
            throw new IllegalArgumentException("DELIVERY_MATERIAL_ARCHIVE_STATE_INVALID");
        }
    }
}
