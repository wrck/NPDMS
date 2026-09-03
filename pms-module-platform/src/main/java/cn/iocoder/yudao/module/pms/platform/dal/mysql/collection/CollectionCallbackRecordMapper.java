package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionCallbackRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionCallbackRecordMapper extends BaseMapperX<CollectionCallbackRecordDO> {

    CollectionCallbackRecordDO selectByTenantAndCallbackId(
            @Param("tenantId") Long tenantId,
            @Param("callbackId") String callbackId);
}
