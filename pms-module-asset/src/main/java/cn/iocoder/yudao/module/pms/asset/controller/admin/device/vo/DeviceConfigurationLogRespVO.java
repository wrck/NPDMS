package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备配置日志 Response VO（ast_device_config_log 承载）
 */
@Data
public class DeviceConfigurationLogRespVO {

    private Long id;
    private Long deviceId;
    private String configType;
    private String configContent;
    private String sourceSystem;
    private LocalDateTime collectedAt;
    private String fileUrl;
    private String fileHash;
    private String remark;
    private LocalDateTime createTime;
}
