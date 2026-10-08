package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** Explicit tenant-scoped CAS for a non-template, unarchived active material. */
public record DeliveryMaterialWithdrawalQuery(Long tenantId, Long materialId, Long expectedVersion, String updater) {
    public DeliveryMaterialWithdrawalQuery {
        if (tenantId == null || tenantId < 0 || materialId == null || materialId <= 0
                || expectedVersion == null || expectedVersion < 0 || updater == null || updater.isBlank()) {
            throw new IllegalArgumentException("DELIVERY_MATERIAL_WITHDRAWAL_INVALID");
        }
    }
}
