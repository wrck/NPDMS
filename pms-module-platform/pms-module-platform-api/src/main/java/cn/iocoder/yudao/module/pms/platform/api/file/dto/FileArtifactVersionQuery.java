package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record FileArtifactVersionQuery(
        Long artifactId,
        Integer versionNo,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        String requiredAction) {

    public FileArtifactVersionQuery {
        if (artifactId == null || artifactId <= 0 || versionNo == null || versionNo <= 0) {
            throw new IllegalArgumentException("invalid file artifact version key");
        }
        ownerContext = FileActionCodes.requireText(ownerContext, "ownerContext");
        objectType = FileActionCodes.requireText(objectType, "objectType");
        objectId = FileActionCodes.requireText(objectId, "objectId");
        purposeCode = FileActionCodes.requireText(purposeCode, "purposeCode");
        referenceKey = FileActionCodes.requireText(referenceKey, "referenceKey");
        requiredAction = FileActionCodes.requireSupported(requiredAction);
    }
}
