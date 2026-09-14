package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record FileBusinessObjectReferenceSetQuery(Long tenantId, Long actorUserId,
                                                  FileReferenceSetKey key, String requiredAction, tools.jackson.databind.JsonNode ownerExecutionContext) {
    public FileBusinessObjectReferenceSetQuery(Long tenantId, Long actorUserId, FileReferenceSetKey key, String requiredAction) {
        this(tenantId, actorUserId, key, requiredAction, null);
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    public FileBusinessObjectReferenceSetQuery {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
        if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0 || key == null) {
            throw new IllegalArgumentException("invalid trusted reference set policy context");
        }
        requiredAction = FileActionCodes.requireSupported(requiredAction);
    }
}
