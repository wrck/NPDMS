package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultEvidenceScanDO;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** 完整证据判断通知仍不是节点完成回执。 */
public record ResultEvidenceEvaluatedEvent(String eventId, int eventVersion, ResultSubscriptionWakeup target, Long scanId) {
    public static final String EVENT_TYPE = "PMS.ResultEvidenceEvaluated.v1";
    public ResultEvidenceEvaluatedEvent {
        UUID.fromString(eventId); Objects.requireNonNull(target, "evidence target");
        if (eventVersion != 1 || scanId == null || scanId <= 0) throw new IllegalArgumentException("EVIDENCE_EVALUATED_EVENT_INVALID");
    }
    public static ResultEvidenceEvaluatedEvent create(ResultSubscriptionWakeup target, ResultEvidenceScanDO scan) {
        return new ResultEvidenceEvaluatedEvent(UUID.nameUUIDFromBytes(("evidence-evaluated:" + scan.getId()).getBytes(StandardCharsets.UTF_8)).toString(),1,target,scan.getId());
    }
}
