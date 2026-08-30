package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record AuthenticatedAssistedFileRevalidationQuery(
        Long tenantId, Long actorUserId, Long taskId, Long questionnaireId,
        String requestId, Long responseId, List<AuthenticatedAssistedFileHandle> files) {
    public AuthenticatedAssistedFileRevalidationQuery {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
