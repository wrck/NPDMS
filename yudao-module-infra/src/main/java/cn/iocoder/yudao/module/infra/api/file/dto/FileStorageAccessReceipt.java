package cn.iocoder.yudao.module.infra.api.file.dto;

import java.time.LocalDateTime;

public record FileStorageAccessReceipt(
        String shortLivedUrl,
        LocalDateTime expiresAt) {
}
