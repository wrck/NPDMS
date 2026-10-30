package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import java.util.*;

/** 前序只消费完成事实；任何当前记录未完成或设备未覆盖都不能提升为完成。 */
public final class ImplementationReadinessRules {
    private ImplementationReadinessRules() { }
    public record Record(Long id, Long version, Long deviceId, boolean completed) { }

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
