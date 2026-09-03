package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceContractException;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.OwnerFactVersionMismatchException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** AST公开设备范围事实到IMP到货签收内部端口的最窄适配。 */
@Component
public class DeviceScopeFactApiAdapter implements DeviceScopeFactPort {

    private final DeviceScopeFactApi deviceScopeFactApi;

    public DeviceScopeFactApiAdapter(DeviceScopeFactApi deviceScopeFactApi) {
        this.deviceScopeFactApi = deviceScopeFactApi;
    }

    @Override
    public DeviceScopeFactPort.DeviceScopeFact resolveBySerials(Long tenantId, Long projectId,
                                                                 Set<String> serialNumbers) {
        requireNonEmpty(serialNumbers, "serialNumbers");
        try {
            List<String> orderedSerials = serialNumbers.stream()
                    .sorted(Comparator.comparing(DeviceScopeResolveQuery::comparisonKey))
                    .toList();
            DeviceScopeResolutionResult result = deviceScopeFactApi.resolveBySerials(
                    new DeviceScopeResolveQuery(tenantId, projectId, orderedSerials));
            if (result == null) {
                throw internalCorruption("AST device scope resolution result is missing");
            }
            if (result.decision() == DeviceScopeResolutionResult.Decision.INVALID) {
                throw ArrivalAcceptanceContractException.simple(
                        "BUSINESS_GATE_INVALID", "DEVICE_SCOPE_INVALID",
                        "device scope is not eligible for arrival acceptance");
            }
            return mapFact(result.fact(), tenantId, projectId);
        } catch (DeviceScopeFactException exception) {
            throw mapPublicFailure(exception);
        }
    }

    @Override
    public DeviceScopeFactPort.DeviceScopeFact lockAndRevalidate(
            Long tenantId, Long projectId, List<ExpectedDeviceFact> expectedDevices) {
        requireNonEmpty(expectedDevices, "expectedDevices");
        try {
            List<DeviceScopeRevalidationQuery.ExpectedDevice> expected = expectedDevices.stream()
                    .map(device -> new DeviceScopeRevalidationQuery.ExpectedDevice(
                            device.deviceId(), device.serialNumber(), device.projectAssignmentVersion()))
                    .toList();
            DeviceScopeRevalidationQuery.ExpectedScopeWatermark watermark =
                    new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(expected.stream()
                            .map(device -> new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(
                                    device.deviceId(), device.projectAssignmentVersion()))
                            .toList());
            DeviceScopeRevalidationResult result = deviceScopeFactApi.lockAndRevalidate(
                    new DeviceScopeRevalidationQuery(tenantId, projectId, expected, watermark));
            if (result == null) {
                throw internalCorruption("AST device scope revalidation result is missing");
            }
            if (result.decision() != DeviceScopeRevalidationResult.Decision.VALID) {
                throw new OwnerFactVersionMismatchException(
                        "AST", "DEVICE_ASSIGNMENT_STALE",
                        "device assignment scope is no longer current");
            }
            return mapFact(result.currentFact(), tenantId, projectId);
        } catch (DeviceScopeFactException exception) {
            throw mapPublicFailure(exception);
        }
    }

    private static DeviceScopeFactPort.DeviceScopeFact mapFact(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact fact,
                                                                Long tenantId, Long projectId) {
        if (fact == null || !tenantId.equals(fact.tenantId()) || !projectId.equals(fact.projectId())) {
            throw internalCorruption("AST device scope fact identity does not match the trusted query");
        }
        List<DeviceFact> devices = fact.devices().stream()
                .map(device -> new DeviceFact(device.deviceId(), device.serialNumber(),
                        device.currentProjectId(), device.projectAssignmentVersion()))
                .toList();
        return new DeviceScopeFactPort.DeviceScopeFact(fact.projectId(), devices);
    }

    private static RuntimeException mapPublicFailure(DeviceScopeFactException exception) {
        if (exception.getCode() == DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE) {
            ArrivalAcceptanceContractException mapped = ArrivalAcceptanceContractException.owner(
                    "OWNER_PROVIDER_UNAVAILABLE", "AST_PROVIDER_UNAVAILABLE", "AST",
                    "AST device scope provider is unavailable");
            mapped.initCause(exception);
            return mapped;
        }
        return new IllegalStateException("AST device scope contract or owner data is invalid", exception);
    }

    private static IllegalStateException internalCorruption(String message) {
        return new IllegalStateException(message, new DeviceScopeFactException(
                DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, message));
    }

    private static void requireNonEmpty(java.util.Collection<?> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("AST device scope " + field + " must not be empty",
                    new DeviceScopeFactException(DeviceScopeFactException.Code.INVALID_REQUEST,
                            field + " must not be empty"));
        }
    }
}
