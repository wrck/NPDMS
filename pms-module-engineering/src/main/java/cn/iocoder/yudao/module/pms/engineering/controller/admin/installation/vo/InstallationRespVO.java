package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 硬件安装 Response VO（FR-ENG-022）。
 */
@Schema(description = "管理后台 - 硬件安装 Response VO")
@Data
public class InstallationRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "安装编码", example = "INS-2026-001")
    private String code;

    @Schema(description = "设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "安装位置")
    private String installLocation;

    @Schema(description = "安装时间")
    private LocalDateTime installTime;

    @Schema(description = "安装人", example = "1")
    private Long installerUserId;

    @Schema(description = "环境检查")
    private String environmentCheck;

    @Schema(description = "安装规范检查")
    private String specCheck;

    @Schema(description = "安装照片")
    private String photoUrl;

    @Schema(description = "安装结果")
    private String result;

    @Schema(description = "状态：0 待安装 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
