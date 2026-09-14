package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileBusinessObjectReferenceSetRevalidationQuery(Long tenantId, Long actorUserId,
                                                              FileReferenceSetKey key, String requiredAction,
                                                              Long expectedScopeVersion, tools.jackson.databind.JsonNode ownerExecutionContext) {
    public FileBusinessObjectReferenceSetRevalidationQuery(Long tenantId, Long actorUserId, FileReferenceSetKey key, String requiredAction, Long expectedScopeVersion) {
        this(tenantId, actorUserId, key, requiredAction, expectedScopeVersion, null);
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    public FileBusinessObjectReferenceSetRevalidationQuery {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
        FileBusinessObjectReferenceSetQuery validated = new FileBusinessObjectReferenceSetQuery(
                tenantId, actorUserId, key, requiredAction);
        requiredAction = validated.requiredAction();
        if (expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid reference set scope version");
        }
    }

    public FileBusinessObjectReferenceSetQuery toInspectionQuery() {
        return new FileBusinessObjectReferenceSetQuery(tenantId, actorUserId, key, requiredAction, ownerExecutionContext);
    }
}
