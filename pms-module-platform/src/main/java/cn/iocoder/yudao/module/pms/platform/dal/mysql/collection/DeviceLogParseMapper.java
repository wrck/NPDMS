package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceLogParseDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.DeviceLogParsePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceLogParseMapper extends BaseMapperX<DeviceLogParseDO> {
    default DeviceLogParseDO selectByRequestKey(Long tenantId, String key) {
        return selectOne(new LambdaQueryWrapperX<DeviceLogParseDO>()
                .eq(DeviceLogParseDO::getTenantId, tenantId).eq(DeviceLogParseDO::getRequestKey, key));
    }

    DeviceLogParseDO selectByTenantAndIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    DeviceLogParseDO selectByRequestKeyForUpdate(@Param("tenantId") Long tenantId, @Param("key") String key);

    default PageResult<DeviceLogParseDO> selectPage(DeviceLogParsePageQuery query) {
        var page = new PageParam();
        page.setPageNo(query.pageNo());
        page.setPageSize(query.pageSize());
        var wrapper = new LambdaQueryWrapperX<DeviceLogParseDO>()
                .eq(DeviceLogParseDO::getTenantId, query.tenantId())
                .eq(DeviceLogParseDO::getProjectId, query.projectId());
        // The multi-megabyte immutable envelope is needed only by the detail read.
        wrapper.select(DeviceLogParseDO.class,field -> !"resultSnapshotJson".equals(field.getProperty()));
        if (query.sourceExecutionId() == null) {
            wrapper.isNull(DeviceLogParseDO::getSourceExecutionId);
        } else {
            wrapper.eq(DeviceLogParseDO::getSourceEntry, query.sourceEntry())
                    .eq(DeviceLogParseDO::getSourceObjectId, query.sourceObjectId())
                    .eq(DeviceLogParseDO::getSourceExecutionId, query.sourceExecutionId());
        }
        return selectPage(page, wrapper.orderByDesc(DeviceLogParseDO::getId));
    }
}
