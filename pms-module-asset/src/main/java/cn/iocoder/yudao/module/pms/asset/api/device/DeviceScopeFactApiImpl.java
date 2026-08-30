package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeFact;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeInvalidItem;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceScopeSerialListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceScopeFactApiImpl implements DeviceScopeFactApi {

    private static final Set<String> ELIGIBLE_STATUSES =
            Set.of("ACTIVE", "IN_STOCK", "IN_USE", "FAULT", "REPAIRING");

    private final DeviceMapper deviceMapper;

    @Override
    @Transactional(readOnly = true)
    public DeviceScopeResolutionResult resolveBySerials(DeviceScopeResolveQuery query) {
        if (query == null) {
            throw failure(DeviceScopeFactException.Code.INVALID_REQUEST, "query must not be null");
        }
        requireTrustedTenant(query.tenantId());
        List<DeviceDO> rows;
        try {
            rows = deviceMapper.selectListByScopeSerials(
                    new DeviceScopeSerialListQuery(query.tenantId(), query.serialNumbers()));
        } catch (DataAccessException exception) {
            throw unavailable("failed to resolve device scope", exception);
        }
        Map<String, DeviceDO> bySerial = indexBySerial(rows, query.tenantId());
        List<DeviceDO> validRows = new ArrayList<>();
        List<DeviceScopeInvalidItem> invalidItems = new ArrayList<>();
        for (String serialNumber : query.serialNumbers()) {
            DeviceDO row = bySerial.get(DeviceScopeResolveQuery.comparisonKey(serialNumber));
            if (row == null) {
                invalidItems.add(new DeviceScopeInvalidItem(
                        null, serialNumber, DeviceScopeInvalidItem.Reason.NOT_FOUND));
                continue;
            }
            DeviceScopeInvalidItem invalid = invalidItem(row, query.projectId());
            if (invalid == null) {
                validRows.add(row);
            } else {
                invalidItems.add(invalid);
            }
        }
        if (!invalidItems.isEmpty()) {
            return new DeviceScopeResolutionResult(
                    DeviceScopeResolutionResult.Decision.INVALID, null, invalidItems);
        }
        return new DeviceScopeResolutionResult(DeviceScopeResolutionResult.Decision.RESOLVED,
                fact(query.tenantId(), query.projectId(), validRows), List.of());
    }

    @Override
    @Transactional
    public DeviceScopeRevalidationResult lockAndRevalidate(DeviceScopeRevalidationQuery query) {
        if (query == null) {
            throw failure(DeviceScopeFactException.Code.INVALID_REQUEST, "query must not be null");
        }
        requireTrustedTenant(query.tenantId());
        List<Long> expectedIds = query.expectedDevices().stream()
                .map(DeviceScopeRevalidationQuery.ExpectedDevice::deviceId).toList();
        List<DeviceDO> rows;
        try {
            rows = deviceMapper.selectScopeDevicesForUpdate(
                    new DeviceScopeLockQuery(query.tenantId(), expectedIds));
        } catch (DataAccessException exception) {
            throw unavailable("failed to lock device scope", exception);
        }
        Map<Long, DeviceDO> byId = indexById(rows, query);
        List<DeviceDO> validRows = new ArrayList<>();
        List<DeviceScopeInvalidItem> invalidItems = new ArrayList<>();
        for (DeviceScopeRevalidationQuery.ExpectedDevice expected : query.expectedDevices()) {
            DeviceDO row = byId.get(expected.deviceId());
            if (row == null) {
                invalidItems.add(new DeviceScopeInvalidItem(expected.deviceId(), expected.serialNumber(),
                        DeviceScopeInvalidItem.Reason.NOT_FOUND));
                continue;
            }
            DeviceScopeInvalidItem invalid = invalidItem(row, query.projectId());
            if (invalid == null) {
                validRows.add(row);
            } else {
                invalidItems.add(invalid);
            }
        }
        if (!invalidItems.isEmpty()) {
            return new DeviceScopeRevalidationResult(
                    DeviceScopeRevalidationResult.Decision.INVALID, null, invalidItems);
        }
        DeviceScopeFact currentFact = fact(query.tenantId(), query.projectId(), validRows);
        Map<Long, Long> expectedVersions = new HashMap<>();
        query.expectedDevices().forEach(device ->
                expectedVersions.put(device.deviceId(), device.projectAssignmentVersion()));
        boolean stale = currentFact.devices().stream().anyMatch(device ->
                !Objects.equals(expectedVersions.get(device.deviceId()), device.projectAssignmentVersion()));
        return new DeviceScopeRevalidationResult(stale
                ? DeviceScopeRevalidationResult.Decision.STALE
                : DeviceScopeRevalidationResult.Decision.VALID, currentFact, List.of());
    }

    private static Map<String, DeviceDO> indexBySerial(List<DeviceDO> rows, Long tenantId) {
        if (rows == null) {
            throw corrupted("owner serial query returned null");
        }
        Map<String, DeviceDO> bySerial = new HashMap<>();
        for (DeviceDO row : rows) {
            requireOwnerRow(row, tenantId);
            String key = ownerSerialKey(row.getSn());
            if (bySerial.putIfAbsent(key, row) != null) {
                throw corrupted("owner returned duplicate normalized serial identity");
            }
        }
        return bySerial;
    }

    private static Map<Long, DeviceDO> indexById(List<DeviceDO> rows, DeviceScopeRevalidationQuery query) {
        if (rows == null) {
            throw corrupted("owner lock query returned null");
        }
        Map<Long, DeviceDO> byId = new HashMap<>();
        Set<Long> expectedIds = new HashSet<>();
        query.expectedDevices().forEach(device -> expectedIds.add(device.deviceId()));
        for (DeviceDO row : rows) {
            requireOwnerRow(row, query.tenantId());
            if (!expectedIds.contains(row.getId())) {
                throw corrupted("owner lock query returned a device outside the expected set");
            }
            query.requireCurrentSerialIdentity(row.getId(), row.getSn());
            if (byId.putIfAbsent(row.getId(), row) != null) {
                throw corrupted("owner lock query returned a duplicate deviceId");
            }
        }
        return byId;
    }

    private static DeviceScopeInvalidItem invalidItem(DeviceDO row, Long projectId) {
        if (!ELIGIBLE_STATUSES.contains(row.getStatus())) {
            return new DeviceScopeInvalidItem(
                    row.getId(), row.getSn(), DeviceScopeInvalidItem.Reason.STATUS_INELIGIBLE);
        }
        if (!Objects.equals(projectId, row.getProjectId())) {
            return new DeviceScopeInvalidItem(
                    row.getId(), row.getSn(), DeviceScopeInvalidItem.Reason.PROJECT_MISMATCH);
        }
        return null;
    }

    private static DeviceScopeFact fact(Long tenantId, Long projectId, List<DeviceDO> rows) {
        List<DeviceScopeFact.Device> devices = rows.stream()
                .map(row -> new DeviceScopeFact.Device(row.getId(), row.getSn(),
                        row.getProjectId(), row.getProjectAssignmentVersion()))
                .toList();
        List<DeviceScopeFact.WatermarkEntry> watermark = devices.stream()
                .map(device -> new DeviceScopeFact.WatermarkEntry(
                        device.deviceId(), device.projectAssignmentVersion()))
                .toList();
        return new DeviceScopeFact(tenantId, projectId, devices,
                new DeviceScopeFact.ScopeWatermark(watermark));
    }

    private static void requireTrustedTenant(Long tenantId) {
        final Long currentTenantId;
        try {
            currentTenantId = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException exception) {
            throw new DeviceScopeFactException(DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context is missing", exception);
        }
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw failure(DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context does not match query tenant");
        }
    }

    private static void requireOwnerRow(DeviceDO row, Long tenantId) {
        if (row == null || row.getId() == null || row.getId() <= 0
                || !Objects.equals(tenantId, row.getTenantId())) {
            throw corrupted("owner returned an invalid device projection");
        }
    }

    private static String ownerSerialKey(String serialNumber) {
        try {
            return DeviceScopeResolveQuery.comparisonKey(serialNumber);
        } catch (DeviceScopeFactException exception) {
            throw corrupted("owner returned an invalid serial identity");
        }
    }

    private static DeviceScopeFactException unavailable(String message, DataAccessException cause) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, message, cause);
    }

    private static DeviceScopeFactException corrupted(String message) {
        return failure(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }

    private static DeviceScopeFactException failure(DeviceScopeFactException.Code code, String message) {
        return new DeviceScopeFactException(code, message);
    }
}
