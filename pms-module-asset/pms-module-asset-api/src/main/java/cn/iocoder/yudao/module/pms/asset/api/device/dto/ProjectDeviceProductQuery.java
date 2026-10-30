package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import java.util.List;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterGroup;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterMatchMode;

/** 当前项目设备的通用产品条件筛选；调用方已校验项目权限，空条件返回空。 */
public record ProjectDeviceProductQuery(Long tenantId, Long projectId,
                                       List<ProductFilterGroup> productFilters, ProductFilterMatchMode matchMode) {
    public ProjectDeviceProductQuery(Long tenantId, Long projectId, List<ProductFilterGroup> productFilters) {
        this(tenantId, projectId, productFilters, ProductFilterMatchMode.MATCHING);
    }
}
