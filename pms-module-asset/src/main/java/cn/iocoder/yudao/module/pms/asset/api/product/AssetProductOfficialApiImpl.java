package cn.iocoder.yudao.module.pms.asset.api.product;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductOfficialSnapshot;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetProductOfficialApiImpl implements AssetProductOfficialApi {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AssetProductOfficialMapper productOfficialMapper;

    @Override
    public List<ProductOfficialSnapshot> getActiveProductSnapshots(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productOfficialMapper.selectList(new LambdaQueryWrapperX<AssetProductOfficialDO>()
                        .in(AssetProductOfficialDO::getId, productIds)
                        .eq(AssetProductOfficialDO::getStatus, STATUS_ACTIVE)).stream()
                .map(row -> new ProductOfficialSnapshot(row.getId(), row.getProductCode(),
                        row.getProductName(), row.getProductModel()))
                .toList();
    }
}
