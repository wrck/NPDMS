package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import java.time.LocalDateTime;

/**
 * 设备配置日志记录命令（EXE-03：配置调试/业务联调等业务环节手动上传 Log 后归档设备档案）。
 */
public record DeviceConfigLogRecordCommand(
        Long deviceId,
        String configType,
        String sourceSystem,
        LocalDateTime collectedAt,
        String fileUrl,
        String fileHash,
        String remark) {

    public DeviceConfigLogRecordCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("deviceId must be positive");
        }
        if (configType == null || configType.isBlank()) {
            throw new IllegalArgumentException("configType must not be blank");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("fileUrl must not be blank");
        }
    }
}
