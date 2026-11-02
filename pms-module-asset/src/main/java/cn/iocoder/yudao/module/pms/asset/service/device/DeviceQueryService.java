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
            boolean tenantWideRead = accessScopeService.hasTenantWideRead(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery(
                    tenantId, SecurityFrameworkUtils.getLoginUserId(), cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW));
            boolean fullProject = !tenantWideRead && query.projectId() != null && accessScopeService.hasFullProjectView(
                    new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery(
                            tenantId, SecurityFrameworkUtils.getLoginUserId(), query.projectId(),
                            cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW));
            var grants = fullProject || tenantWideRead ? java.util.List.<cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant>of()
                    : accessScopeService.organizationGrants(tenantId, SecurityFrameworkUtils.getLoginUserId());
            var readScope = fullProject || tenantWideRead ? null : accessScopeService.pageReadScope(
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
                    .visibleProjectIds(fullProject ? java.util.Set.of(query.projectId()) : null)
                    .readScope(readScope)
                    .tenantWideRead(tenantWideRead)
                    .organizationGrants(grants).build();
            return fullProject || tenantWideRead ? deviceMapper.selectVisibleDevicePage(scopedQuery)
                    : deviceMapper.selectJoinedDevicePage(scopedQuery);
        }
    }

    public DeviceDO getDevice(Long deviceId) {
        Long tenantId = currentTenantId();
        if (deviceId == null || deviceId <= 0 || tenantId == null || SecurityFrameworkUtils.getLoginUser() == null
                || !tenantId.equals(SecurityFrameworkUtils.getLoginUser().getTenantId())) throw deviceNotVisible();
        var rows = getVisibleDevices(new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(
                tenantId, SecurityFrameworkUtils.getLoginUserId(), java.util.Set.of(deviceId), null));
        if (rows.isEmpty()) throw deviceNotVisible();
        return rows.getFirst();
    }

    public java.util.List<DeviceDO> getVisibleDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query) {
        requireBatchActor(query);
        if (query.deviceIds().isEmpty()) return java.util.List.of();
        try (var ignored = cn.iocoder.yudao.framework.common.util.authorization.AuthorizationReadScope.open()) {
            if (query.projectId() != null && accessScopeService.hasFullProjectView(
                    new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery(
                            query.tenantId(), query.actorId(), query.projectId(),
                            cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW))) {
                var rows = deviceMapper.selectCurrentProjectDevicesByIds(
                        new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CurrentProjectDeviceIdsQuery(
                                query.tenantId(), query.projectId(), query.deviceIds()));
                if (rows.size() == query.deviceIds().size()) return rows;
                var found = rows.stream().map(DeviceDO::getId).collect(java.util.stream.Collectors.toSet());
                var missing = new java.util.HashSet<>(query.deviceIds());
                missing.removeAll(found);
                return java.util.stream.Stream.concat(rows.stream(), visibleDevices(
                                new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(
                                        query.tenantId(), query.actorId(), missing, null)).stream())
                        .sorted(java.util.Comparator.comparing(DeviceDO::getId)).toList();
            }
            return visibleDevices(query);
        }
    }

    private java.util.List<DeviceDO> visibleDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query) {
        var subject = new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery(
                query.tenantId(), query.actorId(), cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW);
        boolean tenantWideRead = accessScopeService.hasTenantWideRead(subject);
        var grants = tenantWideRead ? java.util.List.<cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant>of()
                : accessScopeService.organizationGrants(query.tenantId(), query.actorId());
        var readScope = tenantWideRead ? null : accessScopeService.pageReadScope(subject);
        return deviceMapper.selectJoinedDeviceIds(
                new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDeviceIdsQuery(
                        query.tenantId(), query.deviceIds(), null, grants, readScope, tenantWideRead));
    }

    private void requireBatchActor(cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query) {
        var actor = SecurityFrameworkUtils.getLoginUser();
        if (query == null || actor == null || currentTenantId() == null
                || !java.util.Objects.equals(query.tenantId(), currentTenantId())
                || !java.util.Objects.equals(query.tenantId(), actor.getTenantId())
                || !java.util.Objects.equals(query.actorId(), actor.getId())
                || query.deviceIds() == null || query.deviceIds().stream().anyMatch(id -> id == null || id <= 0))
            throw new SecurityException("DEVICE_SUMMARY_SCOPE_REQUIRED");
    }

    private static RuntimeException deviceNotVisible() {
        return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<DeviceDO> getProjectDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query) {
        Long tenantId = currentTenantId();
        if (query == null || tenantId == null || !tenantId.equals(query.tenantId())
                || query.projectId() == null || query.projectId() <= 0) throw new SecurityException("PROJECT_DEVICE_SCOPE_REQUIRED");
        boolean fullProject = accessScopeService.hasFullProjectView(
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery(
                        tenantId, SecurityFrameworkUtils.getLoginUserId(), query.projectId(),
                        cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW));
        var devices = deviceMapper.selectCurrentProjectDevices(query);
        if (fullProject || devices.isEmpty()) return devices;
        var request = new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(
                tenantId, SecurityFrameworkUtils.getLoginUserId(), devices.stream().map(DeviceDO::getId)
                .collect(java.util.stream.Collectors.toSet()), null);
        requireBatchActor(request);
        var visible = getVisibleDevices(request).stream().collect(java.util.stream.Collectors.toMap(
                DeviceDO::getId, java.util.function.Function.identity()));
        // 缺项仍按原设备顺序由单设备入口抛原错误，不能返回部分项目范围。
        return devices.stream().map(device -> visible.containsKey(device.getId())
                ? visible.get(device.getId()) : getDevice(device.getId())).toList();
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
