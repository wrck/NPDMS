package cn.iocoder.yudao.module.pms.asset.dal.mysql.product;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetProductOfficialMapper extends BaseMapperX<AssetProductOfficialDO> {
    default java.util.List<AssetProductOfficialDO> selectDisplayByCodes(
            cn.iocoder.yudao.module.pms.asset.api.product.ProductDisplayQuery query) {
        if (query.productCodes()==null || query.productCodes().isEmpty()) return java.util.List.of();
        return selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<AssetProductOfficialDO>()
                .eq(AssetProductOfficialDO::getTenantId,query.tenantId())
                .in(AssetProductOfficialDO::getProductCode,query.productCodes())
                .orderByDesc(AssetProductOfficialDO::getId));
    }
}
