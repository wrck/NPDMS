package cn.iocoder.yudao.module.pms.platform.api.export;

import java.util.List;

public record ExportBusinessDataQuery(
        Long tenantId,
        Long actorUserId,
        String normalizedFilter,
        List<String> requestedFields,
        boolean includeFiles,
        Long expectedScopeVersion) {
}
