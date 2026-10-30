package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** 固定订阅检查点的一页证据判断请求，旧请求不会转到新计划或新轮次。 */
public record ResultEvidenceScanEvent(String eventId, int eventVersion, ResultSubscriptionWakeup target, int subscriptionVersion) {
    public static final String EVENT_TYPE = "PMS.ResultEvidenceScan.v1";
    public ResultEvidenceScanEvent {
        UUID.fromString(eventId); Objects.requireNonNull(target, "evidence target");
        if (eventVersion != 1 || subscriptionVersion < 0) throw new IllegalArgumentException("EVIDENCE_SCAN_EVENT_INVALID");
    }
    public static ResultEvidenceScanEvent create(ResultSubscriptionDO row, int version, String cause) {
        if (cause == null || cause.isBlank()) throw new IllegalArgumentException("EVIDENCE_CAUSE_REQUIRED");
        var target = ResultSubscriptionWakeup.forCause(row, "evidence-target");
        String id = UUID.nameUUIDFromBytes(("evidence-scan:" + row.getId() + ":" + version + ":" + cause).getBytes(StandardCharsets.UTF_8)).toString();
        return new ResultEvidenceScanEvent(id, 1, target, version);
    }
}
