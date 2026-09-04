package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.commerce.domain.scope.DeliveryScopeValidationRules;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.ScopeDetail;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.ScopeLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.*;

/** AST设备与地点事实的最窄消费适配；无SN数量不调用DeviceScopeFactApi。 */
@Component
@RequiredArgsConstructor
public class DeviceAndLocationFactAdapter {

    private final DeviceScopeFactApi deviceScopeFactApi;
    private final AssetLocationApi assetLocationApi;

    public Snapshot inspect(Long tenantId, Long projectId, List<ScopeLine> lines) {
        List<ScopeDetail> details = lines.stream().flatMap(line -> line.details().stream()).toList();
        validateLocations(details);
        List<String> serials = details.stream().map(ScopeDetail::serialNumber).filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty())
                .sorted(Comparator.comparing(DeliveryScopeValidationRules::serialKey)).toList();
        if (serials.isEmpty()) return new Snapshot(List.of());
        try {
            DeviceScopeResolutionResult result = deviceScopeFactApi.resolveBySerials(
                    new DeviceScopeResolveQuery(tenantId, projectId, serials));
            if (result == null || result.decision() != DeviceScopeResolutionResult.Decision.RESOLVED) {
                throw new CommerceDeliveryScopeCommandException(DEVICE_SCOPE_INVALID,
                        "SN未解析到当前项目的有效设备范围");
            }
            requireFactIdentity(result.fact(), tenantId, projectId, serials);
            return new Snapshot(result.fact().devices().stream().map(device -> new Device(
                    device.deviceId(), device.serialNumber(), device.currentProjectId(),
                    device.projectAssignmentVersion())).toList());
        } catch (CommerceDeliveryScopeCommandException exception) {
            throw exception;
        } catch (DeviceScopeFactException exception) {
            throw mapDeviceFailure(exception);
        }
    }

    public void lockAndRevalidate(Long tenantId, Long projectId, Snapshot expected,
                                  List<ScopeLine> currentLines) {
        validateLocations(currentLines.stream().flatMap(line -> line.details().stream()).toList());
        if (expected.devices().isEmpty()) return;
        List<DeviceScopeRevalidationQuery.ExpectedDevice> devices = expected.devices().stream()
                .map(device -> new DeviceScopeRevalidationQuery.ExpectedDevice(device.deviceId(),
                        device.serialNumber(), device.assignmentVersion())).toList();
        DeviceScopeRevalidationQuery.ExpectedScopeWatermark watermark =
                new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(devices.stream()
                        .map(device -> new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(
                                device.deviceId(), device.projectAssignmentVersion())).toList());
        try {
            DeviceScopeRevalidationResult result = deviceScopeFactApi.lockAndRevalidate(
                    new DeviceScopeRevalidationQuery(tenantId, projectId, devices, watermark));
            if (result == null || result.decision() != DeviceScopeRevalidationResult.Decision.VALID) {
                throw new CommerceDeliveryScopeCommandException(PROJECT_FACT_STALE,
                        "设备归属事实已变化");
            }
            requireFactIdentity(result.currentFact(), tenantId, projectId,
                    expected.devices().stream().map(Device::serialNumber).toList());
            List<Device> current = result.currentFact().devices().stream().map(device -> new Device(
                    device.deviceId(), device.serialNumber(), device.currentProjectId(),
                    device.projectAssignmentVersion())).toList();
            if (!equivalentDevices(expected.devices(), current)) {
                throw new CommerceDeliveryScopeCommandException(PROJECT_FACT_STALE, "设备归属事实已变化");
            }
        } catch (CommerceDeliveryScopeCommandException exception) {
            throw exception;
        } catch (DeviceScopeFactException exception) {
            throw mapDeviceFailure(exception);
        }
    }

    private void validateLocations(List<ScopeDetail> details) {
        try {
            for (ScopeDetail detail : details) {
                if (detail.location().resolution() != CommerceDeliveryScopeCommands.LocationResolution.RESOLVED) {
                    continue;
                }
                SiteRespDTO site = assetLocationApi.getSite(detail.location().siteId(), detail.location().siteVersion());
                SiteLocationRespDTO location = assetLocationApi.getSiteLocation(
                        detail.location().siteLocationId(), detail.location().siteLocationVersion());
                if (site == null || location == null || !CommonStatusEnum.isEnable(site.status())
                        || !CommonStatusEnum.isEnable(location.status())
                        || !Objects.equals(site.id(), location.siteId())) {
                    throw new CommerceDeliveryScopeCommandException(LOCATION_INVALID, "地点事实无效");
                }
            }
        } catch (CommerceDeliveryScopeCommandException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CommerceDeliveryScopeCommandException(OWNER_PROVIDER_UNAVAILABLE,
                    "AST地点事实不可用", exception);
        }
    }

    private RuntimeException mapDeviceFailure(DeviceScopeFactException exception) {
        if (exception.getCode() == DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE) {
            return new CommerceDeliveryScopeCommandException(OWNER_PROVIDER_UNAVAILABLE,
                    "AST设备事实不可用", exception);
        }
        if (exception.getCode() == DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED) {
            return new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED,
                    "AST设备事实损坏", exception);
        }
        return new CommerceDeliveryScopeCommandException(DEVICE_SCOPE_INVALID, "AST设备范围非法", exception);
    }

    private void requireFactIdentity(DeviceScopeFact fact, Long tenantId, Long projectId,
                                     List<String> expectedSerials) {
        if (fact == null || !Objects.equals(fact.tenantId(), tenantId)
                || !Objects.equals(fact.projectId(), projectId)) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "AST设备事实身份不一致");
        }
        Set<String> expected = expectedSerials.stream().map(DeliveryScopeValidationRules::serialKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = fact.devices().stream().map(DeviceScopeFact.Device::serialNumber)
                .map(DeliveryScopeValidationRules::serialKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (expected.size() != expectedSerials.size() || !expected.equals(actual)
                || actual.size() != fact.devices().size()) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "AST设备事实集合不完整");
        }
    }

    private boolean equivalentDevices(List<Device> expected, List<Device> current) {
        if (expected.size() != current.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            Device left = expected.get(index);
            Device right = current.get(index);
            if (!Objects.equals(left.deviceId(), right.deviceId())
                    || !Objects.equals(left.projectId(), right.projectId())
                    || !Objects.equals(left.assignmentVersion(), right.assignmentVersion())
                    || !Objects.equals(DeliveryScopeValidationRules.serialKey(left.serialNumber()),
                    DeliveryScopeValidationRules.serialKey(right.serialNumber()))) {
                return false;
            }
        }
        return true;
    }

    public record Snapshot(List<Device> devices) {
        public Snapshot {
            devices = devices == null ? List.of() : devices.stream().sorted(Comparator.comparing(Device::deviceId)).toList();
        }
    }

    public record Device(Long deviceId, String serialNumber, Long projectId, Long assignmentVersion) {
    }
}
