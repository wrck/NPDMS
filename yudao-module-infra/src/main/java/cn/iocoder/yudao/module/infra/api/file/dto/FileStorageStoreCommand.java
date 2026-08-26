package cn.iocoder.yudao.module.infra.api.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record FileStorageStoreCommand(
        @NotBlank(message = "存储操作标识不能为空") String storageOperationId,
        @NotEmpty(message = "文件内容不能为空") byte[] validatedContent,
        @NotBlank(message = "文件名称不能为空") String name,
        @NotBlank(message = "媒体类型不能为空") String mediaType) {
}
