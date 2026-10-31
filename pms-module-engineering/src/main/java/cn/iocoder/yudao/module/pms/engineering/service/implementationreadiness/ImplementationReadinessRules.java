package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import java.util.*;

/** 前序只消费完成事实；任何当前记录未完成或设备未覆盖都不能提升为完成。 */
public final class ImplementationReadinessRules {
    private ImplementationReadinessRules() { }
    public record Record(Long id, Long version, Long deviceId, boolean completed) { }
    public record InstallationScope(Long projectId, Set<Long> exemptDeviceIds, String policyVersion) { }

    public static SourceFact installation(List<Record> records, List<DeviceFact> devices, InstallationScope scope) {
        var required = devices.stream().filter(device -> !scope.exemptDeviceIds().contains(device.deviceId())).toList();
        var fact = milestone(SourceCode.EXE_02, records, required.isEmpty() ? devices : required);
        if (fact.sourceObjectIds().isEmpty()) return fact;
        var marks = new ArrayList<>(fact.watermarkEntries());
        // The full digest is encoded in two axes: snapshots remain relational and policy changes make them stale.
        var digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(scope.policyVersion()).toUpperCase(Locale.ROOT);
        marks.add(new WatermarkEntry("INSTALL_POLICY_" + digest.substring(0, 32), scope.projectId(), 0L));
        marks.add(new WatermarkEntry("INSTALL_POLICY_" + digest.substring(32), scope.projectId(), 0L));
        for (var device : devices) {
            if (scope.exemptDeviceIds().contains(device.deviceId()))
                marks.add(new WatermarkEntry("DEVICE_ASSIGNMENT", device.deviceId(), device.projectAssignmentVersion()));
        }
        return new SourceFact(fact.sourceCode(), fact.completionStatus(), fact.factVersion(), fact.sourceObjectIds(), marks, fact.reopened());
    }

    public static SourceFact milestone(SourceCode code, List<Record> records, List<DeviceFact> currentDevices) {
        if (currentDevices.isEmpty()) throw new IllegalArgumentException("EXE_DEVICE_SCOPE_EMPTY");
        var required = currentDevices.stream().map(DeviceFact::deviceId).collect(java.util.stream.Collectors.toSet());
        var current = records.stream().filter(row -> row.deviceId() == null || required.contains(row.deviceId())).toList();
        var covered = current.stream().filter(Record::completed).map(Record::deviceId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        boolean complete = !current.isEmpty() && covered.containsAll(required) && current.stream().allMatch(Record::completed);
        var watermarks = new ArrayList<WatermarkEntry>();
        for (var row : current) watermarks.add(new WatermarkEntry(code.name(), row.id(), row.version()));
        if (!current.isEmpty()) currentDevices.forEach(device -> watermarks.add(new WatermarkEntry(
                "DEVICE_ASSIGNMENT", device.deviceId(), device.projectAssignmentVersion())));
        return new SourceFact(code, complete ? CompletionStatus.COMPLETED : CompletionStatus.NOT_COMPLETED,
                current.stream().map(Record::version).max(Long::compareTo).orElse(0L),
                current.stream().map(Record::id).toList(), watermarks, false);
    }
}
