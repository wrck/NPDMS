package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record BusinessGrantFileRevalidationQuery(
        Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, List<BusinessGrantFileHandle> files) {

    public BusinessGrantFileRevalidationQuery {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
