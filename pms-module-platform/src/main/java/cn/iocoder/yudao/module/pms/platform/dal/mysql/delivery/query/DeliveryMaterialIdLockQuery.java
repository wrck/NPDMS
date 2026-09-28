package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

import java.util.List;

/** 材料锁定读场景化查询：归档补偿按ID清单固定材料行。 */
public record DeliveryMaterialIdLockQuery(Long tenantId, List<Long> ids) {

    public DeliveryMaterialIdLockQuery {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("DELIVERY_MATERIAL_LOCK_TENANT_INVALID");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("DELIVERY_MATERIAL_LOCK_IDS_EMPTY");
        }
    }
}
