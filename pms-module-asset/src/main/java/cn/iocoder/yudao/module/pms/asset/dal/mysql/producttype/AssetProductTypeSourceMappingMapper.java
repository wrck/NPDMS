package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingConflictUpdate;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingImportLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetProductTypeSourceMappingMapper
        extends BaseMapperX<AssetProductTypeSourceMappingDO> {

    AssetProductTypeSourceMappingDO selectForUpdate(
            @Param("query") ProductTypeSourceMappingLockQuery query);

    AssetProductTypeSourceMappingDO selectForImportUpdate(
            @Param("query") ProductTypeSourceMappingImportLockQuery query);

    int markConflict(@Param("update") ProductTypeSourceMappingConflictUpdate update);
}
