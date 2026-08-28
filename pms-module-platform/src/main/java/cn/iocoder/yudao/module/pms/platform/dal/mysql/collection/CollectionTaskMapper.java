package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionTaskMapper extends BaseMapperX<CollectionTaskDO> {

    CollectionTaskDO selectByTenantAndPlatformTaskId(@Param("tenantId") Long tenantId,
                                                      @Param("platformTaskId") String platformTaskId);

    List<CollectionTaskDO> selectListByBatchId(Long batchId);
}
