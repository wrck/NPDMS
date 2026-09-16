package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Committed Owner fact. It carries no task, stage, template or execution identity. */
public record BusinessOperationResultEvent(String eventId, int eventVersion, Long tenantId, Long projectId,
        String ownerContext, String objectType, String objectId, String revisionId, Integer objectVersion,
        String businessFactVersion, String resultCode, String operationCode, String commandId,
        Long actorId, LocalDateTime occurredAt, String correlationId) {
    public static final String EVENT_TYPE = "PMS.BusinessOperationResultCommitted.v1";
    public BusinessOperationResultEvent {
        if (eventId == null || eventVersion != 1 || tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || actorId == null || actorId < 0 || occurredAt == null || blank(ownerContext) || blank(objectType)
                || blank(objectId) || objectId.length() > 128 || blank(businessFactVersion) || blank(resultCode)
                || objectVersion == null || objectVersion < 0 || blank(operationCode) || blank(commandId))
            throw new IllegalArgumentException("BUSINESS_RESULT_IDENTITY_INVALID");
        UUID.fromString(eventId);
        if (blank(correlationId)) correlationId = eventId;
    }
    public void requireEnvelope(String id, Long tenant) {
        if (!Objects.equals(eventId,id) || !Objects.equals(tenantId,tenant)) throw new IllegalArgumentException("BUSINESS_RESULT_ENVELOPE_MISMATCH");
    }
    public static BusinessOperationResultEvent create(Long tenant, Long project, String operation, String command,
            ProjectOperationResult result, Long actor, String correlation) {
        return new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,tenant,project,result.ownerContext(),result.objectType(),
                result.objectId(),result.revisionId(),result.objectVersion(),result.businessFactVersion(),result.resultCode(),operation,command,
                actor,LocalDateTime.now(),correlation);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
