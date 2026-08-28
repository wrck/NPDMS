package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record AttachExistingFileVersionsCommand(
        String operationId,
        List<AttachExistingFileVersionItem> items) {

    public AttachExistingFileVersionsCommand {
        if (operationId == null || operationId.isBlank() || operationId.trim().length() > 128
                || items == null || items.isEmpty() || items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid existing file attachment command");
        }
        operationId = operationId.trim();
        items = List.copyOf(items);
    }
}
