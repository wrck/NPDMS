package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 配置调试 Response VO（FR-ENG-023）。
 */
@Schema(description = "管理后台 - 配置调试 Response VO")
@Data
public class ConfigurationRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "配置编码", example = "CFG-2026-001")
    private String code;

    @Schema(description = "设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "配置 Log 文件")
    private String configLogUrl;

    @Schema(description = "调试结果")
    private String debugResult;

    @Schema(description = "调试人", example = "1")
    private Long debuggerUserId;

    @Schema(description = "调试时间")
    private LocalDateTime debugTime;

    @Schema(description = "配置档案快照")
    private String configSnapshot;

    @Schema(description = "状态：0 待调试 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
