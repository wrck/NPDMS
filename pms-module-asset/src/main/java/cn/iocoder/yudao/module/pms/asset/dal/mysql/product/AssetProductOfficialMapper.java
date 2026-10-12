package cn.iocoder.yudao.module.pms.asset.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogIdentityQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogCodesQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface AssetProductOfficialMapper extends BaseMapperX<AssetProductOfficialDO> {
    AssetProductOfficialDO selectByProductCodeForUpdate(@Param("query") ProductCatalogIdentityQuery query);

    default List<AssetProductOfficialDO> selectShipmentProducts(ProductCatalogCodesQuery query) {
        if (query == null || query.tenantId() == null || query.productCodes() == null || query.productCodes().isEmpty())
            return List.of();
        return selectList(new LambdaQueryWrapperX<AssetProductOfficialDO>()
                .select(AssetProductOfficialDO::getId, AssetProductOfficialDO::getTenantId,
                        AssetProductOfficialDO::getProductCode, AssetProductOfficialDO::getProductName,
                        AssetProductOfficialDO::getProductModel)
                .eq(AssetProductOfficialDO::getTenantId, query.tenantId())
                .in(AssetProductOfficialDO::getProductCode, query.productCodes()));
    }
}
