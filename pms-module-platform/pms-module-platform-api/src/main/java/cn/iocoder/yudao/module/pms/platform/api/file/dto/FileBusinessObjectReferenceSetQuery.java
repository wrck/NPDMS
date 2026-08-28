package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record FileBusinessObjectReferenceSetQuery(Long tenantId, Long actorUserId,
                                                  FileReferenceSetKey key, String requiredAction) {
    public FileBusinessObjectReferenceSetQuery {
        if (tenantId == null || tenantId < 0 || actorUserId == null || actorUserId <= 0 || key == null) {
            throw new IllegalArgumentException("invalid trusted reference set policy context");
        }
        requiredAction = FileActionCodes.requireSupported(requiredAction);
    }
}
