package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import java.time.LocalDateTime;

public record DeviceConfigurationLogMetadata(
        Long id,
        String configType,
        String sourceSystem,
        LocalDateTime collectedAt,
        String fileHash,
        String remark,
        boolean downloadable) {
}
