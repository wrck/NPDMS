package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record AuthenticatedAssistedFilesRevalidationCommand(
        Long tenantId, Long taskId, Long questionnaireId, String requestId,
        Long responseId, List<AuthenticatedAssistedFileHandle> files) {
    public AuthenticatedAssistedFilesRevalidationCommand {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
