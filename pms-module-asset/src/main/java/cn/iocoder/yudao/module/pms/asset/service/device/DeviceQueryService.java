package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceQueryService {

    private final DeviceMapper deviceMapper;
    private final DeviceAccessScopeService accessScopeService;
    private final cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi projects;
    private final cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi customers;

    public PageResult<DeviceListProjection> getPage(VisibleDevicePageQuery query) {
        try (var scope = cn.iocoder.yudao.framework.common.util.authorization.AuthorizationReadScope.open()) {
            Long tenantId = currentTenantId();
            if (tenantId == null || query.tenantId() != null && !tenantId.equals(query.tenantId())) {
                return PageResult.empty();
            }
            var matchingProjects = query.projectName() == null || query.projectName().isBlank() ? null
                    : java.util.Objects.requireNonNull(projects.findProjectIdsByName(new cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi.NameQuery(tenantId, query.projectName())));
            var matchingCustomers = query.customerName() == null || query.customerName().isBlank() ? null
                    : java.util.Objects.requireNonNull(customers.findCustomerIdsByName(new cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi.NameQuery(tenantId, query.customerName())));
            if (matchingProjects != null && matchingProjects.isEmpty() || matchingCustomers != null && matchingCustomers.isEmpty()) return PageResult.empty();
            var grants = accessScopeService.organizationGrants(tenantId, SecurityFrameworkUtils.getLoginUserId());
            var readScope = accessScopeService.pageReadScope(
                    new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery(
                            tenantId, SecurityFrameworkUtils.getLoginUserId(),
                            cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW));
            VisibleDevicePageQuery scopedQuery = VisibleDevicePageQuery.builder()
                    .tenantId(tenantId)
                    .sn(query.sn())
                    .productCode(query.productCode())
                    .projectId(query.projectId())
                    .customerId(query.customerId())
                    .pageNo(query.pageNo())
                    .pageSize(query.pageSize())
                    .name(query.name())
                    .status(query.status())
                    .departmentName(query.departmentName() == null ? null : query.departmentName().trim())
                    .matchingProjectIds(matchingProjects)
                    .matchingCustomerIds(matchingCustomers)
                    .readScope(readScope)
                    .organizationGrants(grants).build();
            return deviceMapper.selectJoinedDevicePage(scopedQuery);
        }
    }

    public DeviceDO getDevice(Long deviceId) {
        Long tenantId = currentTenantId();
        accessScopeService.assertVisible(tenantId, SecurityFrameworkUtils.getLoginUserId(), deviceId);
        DeviceDO device = deviceMapper.selectById(deviceId);
        if (device == null || tenantId == null || !tenantId.equals(device.getTenantId())) {
            return null;
        }
        return device;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<DeviceDO> getProjectDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query) {
        Long tenantId = currentTenantId();
        if (query == null || tenantId == null || !tenantId.equals(query.tenantId())
                || query.projectId() == null || query.projectId() <= 0) throw new SecurityException("PROJECT_DEVICE_SCOPE_REQUIRED");
        var projectIds = accessScopeService.visibleProjectIds(tenantId, SecurityFrameworkUtils.getLoginUserId());
        var devices = deviceMapper.selectCurrentProjectDevices(query);
        if (projectIds.contains(query.projectId())) return devices;
        // 合同和组织授权仍经过既有逐设备判断，不能把项目设备清单当成授权。
        return devices.stream().map(device -> getDevice(device.getId())).toList();
    }

    /** 按设备条件解析当前归属项目ID集合；租户不一致按无命中返回空集合。 */
    public java.util.Set<Long> resolveProjectIds(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceProjectMatchQuery query) {
        Long tenantId = currentTenantId();
        if (tenantId == null || query.tenantId() == null || !tenantId.equals(query.tenantId())) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(deviceMapper.selectListProjectIdsByMatch(
                new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceProjectFilterQuery(
                        query.tenantId(), query.deviceSnKeyword(),
                        query.deviceProductModelKeyword(), query.deviceWarrantyStatus())));
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getTenantId();
    }
}
