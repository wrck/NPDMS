package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CustomerDeviceSummaryPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeSerialListQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.DeviceLocationProjectionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapperX<DeviceDO> {

    default DeviceDO selectByTenantAndSn(Long tenantId, String sn) {
        return selectOne(new LambdaQueryWrapperX<DeviceDO>()
                .eq(DeviceDO::getTenantId, tenantId)
                .eq(DeviceDO::getSn, sn));
    }

    default DeviceDO selectByTenantAndId(Long tenantId, Long id) {
        return selectOne(new LambdaQueryWrapperX<DeviceDO>()
                .eq(DeviceDO::getTenantId, tenantId)
                .eq(DeviceDO::getId, id));
    }

    DeviceDO selectByTenantAndIdForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id);

    List<DeviceDO> selectListByScopeSerials(@Param("query") DeviceScopeSerialListQuery query);

    List<DeviceDO> selectScopeDevicesForUpdate(@Param("query") DeviceScopeLockQuery query);

    int updateLocationProjection(@Param("update") DeviceLocationProjectionUpdate update);

    default PageResult<DeviceListProjection> selectVisibleDevicePage(VisibleDevicePageQuery query) {
        PageResult<DeviceListProjection> empty = emptyWhenInvisible(query);
        if (empty != null) {
            return empty;
        }
        return new PageResult<>(selectVisibleDeviceList(query), selectVisibleDeviceCount(query));
    }

    static PageResult<DeviceListProjection> emptyWhenInvisible(VisibleDevicePageQuery query) {
        if (query.visibleProjectIds() != null && query.visibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        return null;
    }

    default PageResult<DeviceDO> selectCustomerSummaryPage(CustomerDeviceSummaryPageQuery query) {
        if (query.visibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        long total = selectCustomerSummaryCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectCustomerSummaryList(query), total);
    }

    List<DeviceDO> selectCustomerSummaryList(@Param("query") CustomerDeviceSummaryPageQuery query);

    long selectCustomerSummaryCount(@Param("query") CustomerDeviceSummaryPageQuery query);

    List<DeviceListProjection> selectVisibleDeviceList(@Param("query") VisibleDevicePageQuery query);

    long selectVisibleDeviceCount(@Param("query") VisibleDevicePageQuery query);

    boolean existsVisibleDevice(@Param("query") DeviceVisibilityQuery query);
}
