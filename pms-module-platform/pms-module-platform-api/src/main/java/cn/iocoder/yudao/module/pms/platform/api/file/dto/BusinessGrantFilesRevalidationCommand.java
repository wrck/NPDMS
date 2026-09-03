package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record BusinessGrantFilesRevalidationCommand(
        Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, List<BusinessGrantFileHandle> files) {

    public BusinessGrantFilesRevalidationCommand {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
