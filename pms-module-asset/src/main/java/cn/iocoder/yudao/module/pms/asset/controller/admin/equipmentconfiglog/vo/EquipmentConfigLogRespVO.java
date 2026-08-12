package cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 设备配置日志 Response VO（FR-RES-003）。
 */
@Schema(description = "管理后台 - 设备配置日志 Response VO")
@Data
public class EquipmentConfigLogRespVO {

    @Schema(description = "配置日志编号", example = "1024")
    private Long id;

    @Schema(description = "设备编号", example = "2048")
    private Long equipmentId;

    @Schema(description = "配置类型", example = "RUNNING_CONFIG")
    private String configType;

    @Schema(description = "配置内容", example = "interface GE0/0/1 ...")
    private String configContent;

    @Schema(description = "来源系统", example = "NMS")
    private String sourceSystem;

    @Schema(description = "采集时间", example = "2026-01-01 12:00:00")
    private LocalDateTime collectedAt;

    @Schema(description = "配置文件URL", example = "https://oss.example.com/config/1024.txt")
    private String fileUrl;

    @Schema(description = "配置文件哈希", example = "a1b2c3d4e5f6")
    private String fileHash;

    @Schema(description = "备注", example = "巡检采集")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
