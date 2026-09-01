package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

import java.time.LocalDateTime;

public record PatchApprovedContactResult(Long taskId, Long planRevisionId, Integer planVersion,
                                         Long arrangementId, ContactSnapshot before, ContactSnapshot after,
                                         Long changedBy, String reasonCode, LocalDateTime changedAt, boolean replayed) {

    public PatchApprovedContactResult replayedCopy() {
        return replayed ? this : new PatchApprovedContactResult(taskId, planRevisionId, planVersion,
                arrangementId, before, after, changedBy, reasonCode, changedAt, true);
    }

    public record ContactSnapshot(String personName, String phone, LocalDateTime arrivalTime) {
    }
}
