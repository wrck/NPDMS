package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionResultConsumptionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.ExistingCollectionConsumptionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionResultConsumptionMapper extends BaseMapperX<CollectionResultConsumptionDO> {

    CollectionResultConsumptionDO selectExisting(
            @Param("query") ExistingCollectionConsumptionQuery query);
}
