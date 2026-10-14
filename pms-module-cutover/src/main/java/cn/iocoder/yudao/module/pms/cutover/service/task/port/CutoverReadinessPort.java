package cn.iocoder.yudao.module.pms.cutover.service.task.port;

import java.util.List;
import lombok.Builder;

/** CUT 对 IMP 实施就绪快照的消费端口。 */
public interface CutoverReadinessPort {

    ReadinessFact inspect(Long projectId, List<Long> deviceIds);

    ReadinessFact lockAndRevalidate(ReadinessFact expected);

    ReadinessFact readSnapshot(SnapshotReference reference);

    @Builder
    record SnapshotReference(Long tenantId, Long projectId, Long snapshotId, Long snapshotVersion,
                             List<Long> deviceIds) { }

    record ReadinessFact(Long snapshotId, long snapshotVersion, String decision, Long projectId,
                         List<Long> deviceIds, Object sourceWatermark, List<String> unmetCodes) {
        public ReadinessFact {
            deviceIds = deviceIds == null ? null : List.copyOf(deviceIds);
            unmetCodes = unmetCodes == null ? null : List.copyOf(unmetCodes);
        }
    }
}
