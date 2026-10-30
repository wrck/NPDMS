package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** 归档失败水位场景化查询：仅当行仍为 PENDING_COMPENSATION 时累加重试计数。 */
public record DeliveryMaterialArchiveRetryQuery(Long tenantId, Long materialId, String failureCode) {

    public DeliveryMaterialArchiveRetryQuery {
        if (tenantId == null || tenantId < 0 || materialId == null || materialId <= 0
                || failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("DELIVERY_MATERIAL_ARCHIVE_RETRY_INVALID");
        }
    }
}
