package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备档案版本历史 Response VO（追加只读）。
 */
@Data
public class DeviceArchiveVersionRespVO {

    private Long id;
    private Long deviceId;
    private Integer versionNo;
    private String changeType;
    private String changeDescription;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String creator;
    private LocalDateTime createTime;
}
