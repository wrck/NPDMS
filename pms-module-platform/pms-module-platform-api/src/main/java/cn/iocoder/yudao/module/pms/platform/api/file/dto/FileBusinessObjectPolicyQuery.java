package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record FileBusinessObjectPolicyQuery(
        Long tenantId,
        Long actorUserId,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        String requiredAction) {

    public FileBusinessObjectPolicyQuery {
        if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("invalid trusted file policy context");
        }
        ownerContext = FileActionCodes.requireText(ownerContext, "ownerContext");
        objectType = FileActionCodes.requireText(objectType, "objectType");
        objectId = FileActionCodes.requireText(objectId, "objectId");
        purposeCode = FileActionCodes.requireText(purposeCode, "purposeCode");
        referenceKey = FileActionCodes.requireText(referenceKey, "referenceKey");
        requiredAction = FileActionCodes.requireSupported(requiredAction);
    }
}
