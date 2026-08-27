package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import java.time.LocalDateTime;

public record DeviceConfigurationDownloadGrant(String downloadPath, LocalDateTime expiresAt) {
}
