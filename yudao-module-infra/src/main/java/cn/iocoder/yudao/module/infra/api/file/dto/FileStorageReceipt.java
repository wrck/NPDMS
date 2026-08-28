package cn.iocoder.yudao.module.infra.api.file.dto;

public record FileStorageReceipt(
        String storageOperationId,
        Long infraFileId,
        String name,
        String mediaType,
        long sizeBytes) {
}
