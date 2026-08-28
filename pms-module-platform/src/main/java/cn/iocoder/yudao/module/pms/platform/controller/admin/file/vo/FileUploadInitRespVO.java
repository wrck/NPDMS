package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import java.time.LocalDateTime;

public record FileUploadInitRespVO(Long artifactId, Long sessionId, LocalDateTime expiresAt) {
}
