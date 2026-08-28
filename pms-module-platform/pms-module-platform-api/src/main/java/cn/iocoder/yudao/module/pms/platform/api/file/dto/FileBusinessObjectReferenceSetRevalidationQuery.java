package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileBusinessObjectReferenceSetRevalidationQuery(Long tenantId, Long actorUserId,
                                                              FileReferenceSetKey key, String requiredAction,
                                                              Long expectedScopeVersion) {
    public FileBusinessObjectReferenceSetRevalidationQuery {
        FileBusinessObjectReferenceSetQuery validated = new FileBusinessObjectReferenceSetQuery(
                tenantId, actorUserId, key, requiredAction);
        requiredAction = validated.requiredAction();
        if (expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid reference set scope version");
        }
    }

    public FileBusinessObjectReferenceSetQuery toInspectionQuery() {
        return new FileBusinessObjectReferenceSetQuery(tenantId, actorUserId, key, requiredAction);
    }
}
