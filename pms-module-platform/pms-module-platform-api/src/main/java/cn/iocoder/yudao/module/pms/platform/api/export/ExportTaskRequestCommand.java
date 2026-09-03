package cn.iocoder.yudao.module.pms.platform.api.export;

import java.util.List;

public record ExportTaskRequestCommand(
        Long tenantId,
        Long actorUserId,
        String operationId,
        String ownerContext,
        String exportType,
        String normalizedFilter,
        List<String> requestedFields,
        boolean includeFiles) {
}
