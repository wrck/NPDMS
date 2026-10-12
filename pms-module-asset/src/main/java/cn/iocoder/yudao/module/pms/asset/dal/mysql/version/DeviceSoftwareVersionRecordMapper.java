package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareVersionHistoryPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareVersionRecordLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.CurrentSoftwareRecordQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareRecordHistoricalWrite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceSoftwareVersionRecordMapper extends BaseMapperX<DeviceSoftwareVersionRecordDO> {
    DeviceSoftwareVersionRecordDO selectForUpdate(@Param("query") DeviceSoftwareVersionRecordLockQuery query);
    int insertHistorical(@Param("row") DeviceSoftwareRecordHistoricalWrite row);

    DeviceSoftwareVersionRecordDO selectCurrentPredecessor(@Param("query") CurrentSoftwareRecordQuery query);

    default DeviceSoftwareVersionRecordDO selectByTenantAndRequest(Long tenantId, String requestKey) {
        return selectOne(new LambdaQueryWrapperX<DeviceSoftwareVersionRecordDO>()
                .eq(DeviceSoftwareVersionRecordDO::getTenantId, tenantId)
                .eq(DeviceSoftwareVersionRecordDO::getRequestKey, requestKey));
    }

    default PageResult<DeviceSoftwareVersionRecordDO> selectHistoryPage(DeviceSoftwareVersionHistoryPageQuery query) {
        if (query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }
        PageParam page = new PageParam();
        page.setPageNo(query.pageNo());
        page.setPageSize(query.pageSize());
        return selectPage(page, new LambdaQueryWrapperX<DeviceSoftwareVersionRecordDO>()
                .eq(DeviceSoftwareVersionRecordDO::getTenantId, query.tenantId())
                .eq(DeviceSoftwareVersionRecordDO::getDeviceId, query.deviceId())
                .in(DeviceSoftwareVersionRecordDO::getProjectId, query.visibleProjectIds())
                .orderByDesc(DeviceSoftwareVersionRecordDO::getRecordedAt)
                .orderByDesc(DeviceSoftwareVersionRecordDO::getId));
    }
    default PageResult<DeviceSoftwareVersionRecordDO> selectProjectHistoryPage(
            cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareProjectHistoryQuery query) {
        PageParam page=new PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<DeviceSoftwareVersionRecordDO>()
                .eq(DeviceSoftwareVersionRecordDO::getTenantId,query.tenantId())
                .eq(DeviceSoftwareVersionRecordDO::getProjectId,query.projectId())
                .eqIfPresent(DeviceSoftwareVersionRecordDO::getDeviceSn,query.deviceSn())
                .orderByDesc(DeviceSoftwareVersionRecordDO::getRecordedAt).orderByDesc(DeviceSoftwareVersionRecordDO::getId));
    }
}
