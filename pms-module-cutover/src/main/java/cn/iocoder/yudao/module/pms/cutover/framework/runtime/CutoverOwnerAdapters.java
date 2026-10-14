package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.asset.api.producttype.AssetProductTypeApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactApi;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException.Code.*;

/** 只消费 Owner 公开事实；参考服务离线不产生伪造的客户等级或实施就绪快照。 */
@Configuration(proxyBeanMethods = false)
public class CutoverOwnerAdapters {

    @Bean
    CutoverProjectScopePort cutoverProjectScopePort(ProjectScopeApi api) {
        return new CutoverProjectScopePort() {
            public ProjectScopeFact inspect(Long actor, Long project, String action) {
                return scope(project, api.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant())
                .subjectUserId(actor)
                .anchorProjectId(project)
                .actionCode(action(action)).build()));
            }
            public ProjectScopeFact lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery query) {
        Long actor = query.actorId();
        Long project = query.projectId();
        String action = query.action();
        long version = query.expectedProjectScopeVersion();

                return scope(project, api.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(tenant())
                .subjectUserId(actor)
                .anchorProjectId(project)
                .actionCode(action(action))
                .expectedScopeVersion(version).build()));
            }
            public Set<Long> resolveAllCurrent(Long actor, String action) {
                return api.resolveAllCurrent(new ProjectAllScopeQuery(tenant(), actor, action(action)));
            }
            private ProjectScopeFact scope(Long project, ProjectScopeResult result) {
                if (result == null) throw unavailable("PROJ 项目权限服务未返回事实");
                return new ProjectScopeFact(project, result.treeVersion(), result.fullProjectIds().contains(project));
            }
        };
    }

    @Bean
    CutoverProjectContextPort cutoverProjectContextPort(ProjectBusinessContextApi api) {
        return new CutoverProjectContextPort() {
            public ProjectContextFact inspect(CutoverProjectContextPort.Inspection query) {
                Long tenantId = query.tenantId();
                Long projectId = query.projectId();
                long scopeVersion = query.expectedProjectScopeVersion();
                requireTenant(tenantId);
                return fact(api.inspect(ProjectBusinessContextApi.Query.builder()
                .tenantId(tenantId)
                .actorId(actor())
                .projectId(projectId)
                .expectedScopeVersion(scopeVersion).build()));
            }
            public ProjectContextFact lockAndRevalidate(ProjectContextFact expected) {
                requireTenant(expected.tenantId());
                return fact(api.lockAndRevalidate(ProjectBusinessContextApi.Revalidation.builder()
                .query(ProjectBusinessContextApi.Query.builder()
                .tenantId(expected.tenantId())
                .actorId(actor())
                .projectId(expected.projectId())
                .expectedScopeVersion(expected.projectScopeVersion()).build())
                .expectedProjectVersion((long) expected.projectVersion()).build()));
            }
            private ProjectContextFact fact(ProjectBusinessContextApi.Context value) {
                if (value == null) throw unavailable("PROJ 项目上下文服务未返回事实");
                return ProjectContextFact.builder()
                        .tenantId(value.tenantId())
                        .projectId(value.projectId())
                        .projectVersion(Math.toIntExact(value.projectVersion()))
                        .projectCode(value.projectCode())
                        .projectName(value.projectName())
                        .customerId(value.customerId())
                        .customerCode(value.customerCode())
                        .customerName(value.customerName())
                        .departmentId(value.departmentId())
                        .departmentCode(value.departmentCode())
                        .departmentName(value.departmentName())
                        .projectScopeVersion(value.scopeVersion())
                        .build();
            }
        };
    }

    @Bean
    CutoverDeviceScopePort cutoverDeviceScopePort(DeviceQueryApi devices, DeviceScopeFactApi api, ProjectScopeApi scopes) {
        return new CutoverDeviceScopePort() {
            public List<DeviceFact> resolveBySerials(List<String> serialNumbers) {
                if (serialNumbers == null || serialNumbers.isEmpty()) return List.of();
                Set<Long> allowed = scopes.resolveAllCurrent(new ProjectAllScopeQuery(tenant(), actor(), ProjectScopeApi.ACTION_VIEW));
                if (allowed.isEmpty()) return List.of();
                Map<Long, DeviceFact> found = new LinkedHashMap<>();
                for (String serial : serialNumbers) {
                    for (Long project : devices.resolveProjectIds(new DeviceProjectMatchQuery(tenant(), serial, null, null))) {
                        if (!allowed.contains(project)) continue;
                        var result = api.resolveBySerials(new DeviceScopeResolveQuery(tenant(), project, List.of(serial)));
                        if (result.decision() == DeviceScopeResolutionResult.Decision.RESOLVED) {
                            map(result.fact()).forEach(device -> found.put(device.deviceId(), device));
                        }
                    }
                }
                return found.values().stream().sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
            }
            public List<DeviceFact> lockAndRevalidate(Long project, List<DeviceFact> expected) {
                var entries = expected.stream().map(value -> new DeviceScopeRevalidationQuery.ExpectedDevice(
                        value.deviceId(), value.serialNumber(), value.projectAssignmentVersion())).toList();
                var watermark = new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(entries.stream().map(value ->
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(value.deviceId(), value.projectAssignmentVersion())).toList());
                var result = api.lockAndRevalidate(new DeviceScopeRevalidationQuery(tenant(), project, entries, watermark));
                if (result.decision() != DeviceScopeRevalidationResult.Decision.VALID) throw new CutoverOwnerFactException(STALE, "设备项目归属已变化");
                return map(result.currentFact());
            }
            private List<DeviceFact> map(cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact fact) {
                requireTenant(fact.tenantId());
                return fact.devices().stream().map(value -> new DeviceFact(value.deviceId(), value.serialNumber(),
                        value.currentProjectId(), value.projectAssignmentVersion())).toList();
            }
        };
    }

    @Bean
    CutoverDeviceProductTypePort cutoverDeviceProductTypePort(AssetProductTypeApi api) {
        return (actor, devices) -> api.getAuthorizedDeviceProductType(new AuthorizedDeviceProductTypeQuery(actor, devices))
                .stream().map(value -> new CutoverDeviceProductTypePort.ProductTypeFact(value.deviceId(), value.productTypeCode(),
                        value.enabled(), value.sourceVersion(), value.resolutionStatus(), value.syncStatus(), value.lastSuccessfulSyncTime(),
                        value.fromLastSuccessfulCopy())).toList();
    }

    @Bean
    CutoverCustomerLevelPort cutoverCustomerLevelPort(ObjectProvider<CustomerServiceLevelFactApi> provider) {
        return new CutoverCustomerLevelPort() {
            public CustomerLevelFact inspect(Long customerId) {
                if (customerId == null || provider.getIfAvailable() == null) throw unavailable("客户服务等级参考暂不可用");
                try { return fact(provider.getObject().inspectCurrent(new CustomerServiceLevelFactQuery(tenant(), customerId))); }
                catch (CustomerServiceLevelFactException error) {
                    if (error.getCode() == CustomerServiceLevelFactException.Code.PROVIDER_UNAVAILABLE) throw unavailable("客户服务等级参考暂不可用");
                    throw error;
                }
            }
            public CustomerLevelFact lockAndRevalidate(CustomerLevelFact expected) {
                if (provider.getIfAvailable() == null) throw unavailable("客户服务等级参考暂不可用");
                return fact(provider.getObject().lockAndRevalidate(new CustomerServiceLevelFactRevalidationQuery(tenant(), expected.customerId(),
                        new ExpectedCustomerServiceLevelFact(CustomerServiceLevelFact.Status.valueOf(expected.status()), tenant(), expected.customerId(),
                                expected.serviceLevelRevisionId(), expected.serviceLevelCode(), expected.factVersion(), expected.effectiveFrom(), expected.effectiveTo()))));
            }
            private CustomerLevelFact fact(CustomerServiceLevelFactResult result) {
                if (result.decision() == CustomerServiceLevelFactResult.Decision.STALE) throw new CutoverOwnerFactException(STALE, "客户等级参考已变化");
                var value = result.currentFact();
                requireTenant(value.tenantId());
                return new CustomerLevelFact(value.status().name(), value.customerId(), null, null, value.serviceLevelRevisionId(),
                        value.serviceLevelCode(), value.factVersion(), value.effectiveFrom(), value.effectiveTo());
            }
        };
    }

    @Bean
    CutoverReadinessPort cutoverReadinessPort(ObjectProvider<ImplementationReadinessApi> provider, DeviceQueryApi devices) {
        return new CutoverReadinessPort() {
            public ReadinessFact inspect(Long projectId, List<Long> deviceIds) {
                return fact(api().inspect(new ImplementationReadinessQuery(tenant(), projectId, expectedDevices(projectId, deviceIds))));
            }
            public ReadinessFact lockAndRevalidate(ReadinessFact expected) {
                return fact(api().lockAndRevalidate(new ImplementationReadinessRevalidationQuery(tenant(), expected.projectId(),
                        expected.snapshotId(), expected.snapshotVersion(), expectedDevices(expected.projectId(), expected.deviceIds()))));
            }
            public ReadinessFact readSnapshot(SnapshotReference reference) {
                requireTenant(reference.tenantId());
                var snapshot = api().readSnapshot(new cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessSnapshotQuery(
                        reference.tenantId(), reference.projectId(), reference.snapshotId(), reference.snapshotVersion()));
                requireTenant(snapshot.tenantId());
                var ids = snapshot.context().devices().stream().map(ImplementationReadinessContextFact.DeviceFact::deviceId).sorted().toList();
                if (!Objects.equals(snapshot.projectId(), reference.projectId()) || !ids.equals(reference.deviceIds().stream().sorted().toList())) {
                    throw new CutoverOwnerFactException(STALE, "冻结就绪快照与任务设备范围不一致");
                }
                return new ReadinessFact(snapshot.snapshotId(), snapshot.snapshotVersion(), snapshot.decision().name(),
                        snapshot.projectId(), ids, snapshot.context(), snapshot.unmetCodes());
            }
            private ImplementationReadinessApi api() {
                var api = provider.getIfAvailable();
                if (api == null) throw unavailable("EXE-06 实施就绪事实服务尚未接入");
                return api;
            }
            private List<ImplementationReadinessQuery.ExpectedDevice> expectedDevices(Long project, List<Long> ids) {
                return ids.stream().map(id -> {
                    var device = devices.getDevice(id);
                    if (device == null || !Objects.equals(device.tenantId(), tenant()) || !Objects.equals(device.projectId(), project)) {
                        throw new CutoverOwnerFactException(STALE, "实施就绪检查的设备范围已变化");
                    }
                    return new ImplementationReadinessQuery.ExpectedDevice(id, device.sn(), device.projectAssignmentVersion());
                }).toList();
            }
            private ReadinessFact fact(ImplementationReadinessResult result) {
                var snapshot = result.snapshot();
                requireTenant(snapshot.tenantId());
                return new ReadinessFact(snapshot.snapshotId(), snapshot.snapshotVersion(), result.decision().name(), snapshot.projectId(),
                        snapshot.context().devices().stream().map(ImplementationReadinessContextFact.DeviceFact::deviceId).toList(),
                        snapshot.context(), result.currentUnmetCodes());
            }
        };
    }

    static Long tenant() {
        var tenant = TenantContextHolder.getTenantId();
        if (tenant == null) throw new SecurityException("缺少受信租户");
        return tenant;
    }
    static Long actor() {
        var actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) throw new SecurityException("缺少受信用户");
        return actor;
    }
    static void requireTenant(Long expected) {
        if (!Objects.equals(tenant(), expected)) throw new SecurityException("租户身份不一致");
    }
    private static String action(String action) {
        return switch (action) {
            case "ACTION_VIEW" -> ProjectScopeApi.ACTION_VIEW;
            case "ACTION_EDIT" -> ProjectScopeApi.ACTION_EDIT;
            default -> throw new IllegalArgumentException("不支持的项目操作");
        };
    }
    private static CutoverOwnerFactException unavailable(String message) {
        return new CutoverOwnerFactException(PROVIDER_UNAVAILABLE, message);
    }
}
