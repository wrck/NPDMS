package cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.collection.ImplementationCollectionLogDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImplementationCollectionLogMapper extends BaseMapperX<ImplementationCollectionLogDO> {
    ImplementationCollectionLogDO findResult(@Param("q") ImplementationCollectionLogResultQuery q);

    default PageResult<ImplementationCollectionLogDO> page(ImplementationCollectionLogPageQuery q) {
        if (q.deviceId() == null || q.projectId() == null) return PageResult.empty();
        var page = new PageParam(); page.setPageNo(q.pageNo()); page.setPageSize(q.pageSize());
        return selectPage(page, new LambdaQueryWrapperX<ImplementationCollectionLogDO>()
                .eq(ImplementationCollectionLogDO::getTenantId, q.tenantId())
                .eq(ImplementationCollectionLogDO::getEntry, q.entry())
                .eq(ImplementationCollectionLogDO::getObjectId, q.objectId())
                .eq(ImplementationCollectionLogDO::getProjectId, q.projectId())
                .eq(ImplementationCollectionLogDO::getDeviceId, q.deviceId())
                .orderByDesc(ImplementationCollectionLogDO::getId));
    }
}
