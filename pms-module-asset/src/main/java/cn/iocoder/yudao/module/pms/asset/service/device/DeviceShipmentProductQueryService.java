package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceShipmentRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogCodesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceShipmentProductQueryService {
    private final AssetProductOfficialMapper products;

    /** Input rows must already be constrained by the device scope and shipment paging query. */
    public PageResult<DeviceShipmentRespVO> withDirectoryLabels(Long tenantId, PageResult<DeviceShipmentDO> page) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("发货目录查询租户不一致");
        var codes = new HashSet<String>();
        for (var row : page.getList()) {
            if (!Objects.equals(row.getTenantId(), tenantId))
                throw new IllegalArgumentException("发货记录不属于当前租户");
            if (directoryCode(row.getProductCode()) != null) codes.add(directoryCode(row.getProductCode()));
            if (directoryCode(row.getSecondaryItem()) != null) codes.add(directoryCode(row.getSecondaryItem()));
        }
        var directory = new HashMap<String, AssetProductOfficialDO>();
        if (!codes.isEmpty()) for (var product : products.selectShipmentProducts(new ProductCatalogCodesQuery(tenantId, codes))) {
            if (product.getId() == null || !Objects.equals(product.getTenantId(), tenantId) || !codes.contains(product.getProductCode())
                    || directory.putIfAbsent(product.getProductCode(), product) != null)
                throw new IllegalStateException("产品目录租户或唯一编码不一致");
        }
        return new PageResult<>(page.getList().stream().map(row -> {
            var response = BeanUtils.toBean(row, DeviceShipmentRespVO.class);
            var primary = directory.get(directoryCode(row.getProductCode()));
            var secondary = directory.get(directoryCode(row.getSecondaryItem()));
            if (primary != null) {
                response.setProductName(primary.getProductName());
                response.setProductModel(primary.getProductModel());
            }
            if (secondary != null) {
                response.setSecondaryProductName(secondary.getProductName());
                response.setSecondaryProductModel(secondary.getProductModel());
            }
            return response;
        }).toList(), page.getTotal());
    }
    private static String directoryCode(String raw) { return raw == null || raw.isBlank() ? null : raw.trim(); }
}
