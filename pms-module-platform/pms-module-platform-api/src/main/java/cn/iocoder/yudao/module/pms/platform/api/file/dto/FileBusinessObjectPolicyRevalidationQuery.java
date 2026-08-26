package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileBusinessObjectPolicyRevalidationQuery(
        Long tenantId,
        Long actorUserId,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        String requiredAction,
        Long expectedScopeVersion) {

    public FileBusinessObjectPolicyRevalidationQuery {
        FileBusinessObjectPolicyQuery validated = new FileBusinessObjectPolicyQuery(
                tenantId, actorUserId, ownerContext, objectType, objectId,
                purposeCode, referenceKey, requiredAction);
        ownerContext = validated.ownerContext();
        objectType = validated.objectType();
        objectId = validated.objectId();
        purposeCode = validated.purposeCode();
        referenceKey = validated.referenceKey();
        requiredAction = validated.requiredAction();
        if (expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid file policy revalidation query");
        }
    }

    public FileBusinessObjectPolicyQuery toInspectionQuery() {
        return new FileBusinessObjectPolicyQuery(tenantId, actorUserId, ownerContext, objectType,
                objectId, purposeCode, referenceKey, requiredAction);
    }
}
