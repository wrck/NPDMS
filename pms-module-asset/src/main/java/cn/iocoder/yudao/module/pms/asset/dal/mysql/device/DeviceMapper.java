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

    java.util.Set<String> selectContractCandidates(@Param("query")
            cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery query);

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

    /** 按租户+项目查询设备编号集合（配置日志项目维度筛选）。 */
    default List<Long> selectIdsByTenantAndProject(Long tenantId, Long projectId) {
        return selectList(new LambdaQueryWrapperX<DeviceDO>()
                .select(DeviceDO::getId)
                .eq(DeviceDO::getTenantId, tenantId)
                .eq(DeviceDO::getProjectId, projectId))
                .stream().map(DeviceDO::getId).toList();
    }

    /** 客户删除守卫：按租户+客户统计设备引用数。 */
    default Long selectCountByCustomer(Long tenantId, Long customerId) {
        return selectCount(new LambdaQueryWrapperX<DeviceDO>()
                .eq(DeviceDO::getTenantId, tenantId)
                .eq(DeviceDO::getCustomerId, customerId));
    }

    /** 站点内部位置删除守卫：统计挂在该位置的设备数。 */
    default Long selectCountBySiteLocationId(Long siteLocationId) {
        return selectCount(new LambdaQueryWrapperX<DeviceDO>()
                .eq(DeviceDO::getSiteLocationId, siteLocationId));
    }

    /** 按 SN 集合批量查询（序列号范围校验）。 */
    default List<DeviceDO> selectListBySns(java.util.Collection<String> sns) {
        return selectList(new LambdaQueryWrapperX<DeviceDO>()
                .in(DeviceDO::getSn, sns));
    }

    /** 按设备条件解析当前归属项目ID集合（project_id 非空，去重）；无命中返回空集合。 */
    default List<Long> selectListProjectIdsByMatch(
            cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceProjectFilterQuery query) {
        LambdaQueryWrapperX<DeviceDO> wrapper = new LambdaQueryWrapperX<DeviceDO>()
                .likeIfPresent(DeviceDO::getSn, query.deviceSnKeyword())
                .likeIfPresent(DeviceDO::getProductModel, query.deviceProductModelKeyword())
                .eqIfPresent(DeviceDO::getWarrantyStatus, query.deviceWarrantyStatus());
        return selectList(wrapper
                .select(DeviceDO::getProjectId)
                .eq(DeviceDO::getTenantId, query.tenantId())
                .isNotNull(DeviceDO::getProjectId))
                .stream().map(DeviceDO::getProjectId).distinct().toList();
    }

    /** 与设备工作台共用当前用户的项目数据范围。 */
    default PageResult<DeviceDO> selectArchivePage(VisibleDevicePageQuery query) {
        if ((query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty())
                && (query.visibleContractNumbers() == null || query.visibleContractNumbers().isEmpty())
                && (query.organizationGrants() == null || query.organizationGrants().isEmpty())) {
            return PageResult.empty();
        }
        return new PageResult<>(selectVisibleArchiveList(query), selectVisibleDeviceCount(query));
    }

    List<DeviceDO> selectVisibleArchiveList(@Param("query") VisibleDevicePageQuery query);

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
        if ((query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty())
                && (query.visibleContractNumbers() == null || query.visibleContractNumbers().isEmpty())
                && (query.organizationGrants() == null || query.organizationGrants().isEmpty())) {
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
