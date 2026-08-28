package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileArtifactVersionRevalidationQuery(
        Long artifactId,
        Integer versionNo,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        String requiredAction,
        FileFactVersion expectedFileFactVersion,
        Long expectedScopeVersion) {

    public FileArtifactVersionRevalidationQuery {
        FileArtifactVersionQuery validated = new FileArtifactVersionQuery(
                artifactId, versionNo, ownerContext, objectType, objectId,
                purposeCode, referenceKey, requiredAction);
        ownerContext = validated.ownerContext();
        objectType = validated.objectType();
        objectId = validated.objectId();
        purposeCode = validated.purposeCode();
        referenceKey = validated.referenceKey();
        requiredAction = validated.requiredAction();
        if (expectedFileFactVersion == null
                || expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid file revalidation query");
        }
    }

    public FileArtifactVersionQuery toInspectionQuery() {
        return new FileArtifactVersionQuery(artifactId, versionNo, ownerContext, objectType,
                objectId, purposeCode, referenceKey, requiredAction);
    }
}
