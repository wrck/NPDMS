package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeCodeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssetProductTypeMapper extends BaseMapperX<AssetProductTypeDO> {

    default List<AssetProductTypeDO> selectByCodes(ProductTypesByCodesQuery query) {
        if (query.productTypeCodes() == null || query.productTypeCodes().isEmpty()) {
            return List.of();
        }
        return selectByCodesInternal(query);
    }

    List<AssetProductTypeDO> selectByCodesInternal(
            @Param("query") ProductTypesByCodesQuery query);

    AssetProductTypeDO selectByCodeForUpdate(
            @Param("query") ProductTypeCodeLockQuery query);
}
