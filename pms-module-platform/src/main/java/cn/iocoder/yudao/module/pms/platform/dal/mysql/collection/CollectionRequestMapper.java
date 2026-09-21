package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionRequestDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionRequestPageQuery;
import org.apache.ibatis.annotations.*;
@Mapper public interface CollectionRequestMapper extends BaseMapperX<CollectionRequestDO> {
    CollectionRequestDO lockRequest(@Param("tenantId") Long tenantId,@Param("key") String key);
    default CollectionRequestDO findByTask(Long tenantId,String taskId) {
        return selectOne(new LambdaQueryWrapperX<CollectionRequestDO>().eq(CollectionRequestDO::getTenantId,tenantId)
            .eq(CollectionRequestDO::getPlatformTaskId,taskId));
    }
    default CollectionRequestDO findRequest(Long tenantId,String key) {
        return selectOne(new LambdaQueryWrapperX<CollectionRequestDO>().eq(CollectionRequestDO::getTenantId,tenantId).eq(CollectionRequestDO::getRequestKey,key));
    }
    default PageResult<CollectionRequestDO> page(CollectionRequestPageQuery q) {
        PageParam p=new PageParam();p.setPageNo(q.pageNo());p.setPageSize(q.pageSize());
        return selectPage(p,new LambdaQueryWrapperX<CollectionRequestDO>().eq(CollectionRequestDO::getTenantId,q.tenantId())
            .eq(CollectionRequestDO::getEntry,q.entry()).eq(CollectionRequestDO::getObjectId,q.objectId()).orderByDesc(CollectionRequestDO::getId));
    }
}
