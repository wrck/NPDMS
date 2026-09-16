package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable per-recipient delivery. Identity pins a recipient; it does not pretend the source is completion proof. */
public record ProjectResultTargetEvent(String eventId, BusinessOperationResultEvent source, String nodeKind,
        Long nodeId, String nodeKey, Long planVersionId, Long executionId, Long contractId) {
    public static final String EVENT_TYPE = "PMS.ProjectBusinessResultTarget.v1";
    public ProjectResultTargetEvent {
        if (source == null || !Set.of("TASK","STAGE").contains(String.valueOf(nodeKind)) || nodeId == null || nodeId <= 0
                || nodeKey == null || nodeKey.isBlank() || planVersionId == null || planVersionId <= 0
                || executionId == null || executionId <= 0 || contractId == null || contractId <= 0
                || !Objects.equals(eventId,id(source.eventId(),nodeKind,nodeId,planVersionId,executionId,contractId)))
            throw new IllegalArgumentException("RESULT_TARGET_IDENTITY_INVALID");
    }
    public static String id(String event, String kind, Long node, Long plan, Long execution, Long contract) {
        return UUID.nameUUIDFromBytes((event + ":" + kind + ":" + node + ":" + plan + ":" + execution + ":" + contract)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
