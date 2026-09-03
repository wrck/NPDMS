package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionBatchProjectionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionBatchMapper extends BaseMapperX<CollectionBatchDO> {

    CollectionBatchDO selectByTenantAndIdempotencyKey(@Param("tenantId") Long tenantId,
                                                       @Param("idempotencyKey") String idempotencyKey);

    int updateProjection(@Param("update") CollectionBatchProjectionUpdate update);
}
