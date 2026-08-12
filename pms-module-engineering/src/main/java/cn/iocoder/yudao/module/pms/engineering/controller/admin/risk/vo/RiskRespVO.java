package cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 单机风险 Response VO（FR-ENG-008）。
 */
@Schema(description = "管理后台 - PMS 单机风险 Response VO")
@Data
public class RiskRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "风险编号", example = "RK-2026-001")
    private String code;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "风险名称", example = "单机部署风险")
    private String name;

    @Schema(description = "风险类型", example = "SINGLE_DEVICE")
    private String riskType;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    private String deviceSerial;

    @Schema(description = "设备型号")
    private String deviceModel;

    @Schema(description = "风险场景描述")
    private String scenario;

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "状态：0 草稿 1 已识别 2 已确认 3 已同步CRM 4 已关闭", example = "0")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "是否已同步CRM")
    private Boolean crmSynced;

    @Schema(description = "CRM同步时间")
    private LocalDateTime crmSyncTime;

    @Schema(description = "处理人", example = "1024")
    private Long handlerUserId;

    @Schema(description = "处理意见")
    private String handleOpinion;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
