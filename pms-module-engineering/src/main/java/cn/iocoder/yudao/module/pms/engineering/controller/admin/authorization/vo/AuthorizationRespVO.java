package cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 授权管理 Response VO（FR-ENG-010）。
 */
@Schema(description = "管理后台 - PMS 授权管理 Response VO")
@Data
public class AuthorizationRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "授权编号", example = "AUTH-2026-001")
    private String code;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "授权名称", example = "FW-2000 临时授权")
    private String name;

    @Schema(description = "授权类型", example = "TEMPORARY")
    private String authorizationType;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    private String deviceSerial;

    @Schema(description = "设备型号")
    private String deviceModel;

    @Schema(description = "授权密钥")
    private String licenseKey;

    @Schema(description = "授权类型描述")
    private String licenseType;

    @Schema(description = "申请开始日期")
    private LocalDate applyStartDate;

    @Schema(description = "申请结束日期")
    private LocalDate applyEndDate;

    @Schema(description = "实际结束日期")
    private LocalDate actualEndDate;

    @Schema(description = "使用次数限制", example = "100")
    private Integer usageLimit;

    @Schema(description = "已使用次数", example = "0")
    private Integer usedCount;

    @Schema(description = "状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "提交人", example = "1024")
    private Long submitUserId;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "审批人", example = "1024")
    private Long approverUserId;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "撤回人", example = "1024")
    private Long recallUserId;

    @Schema(description = "撤回时间")
    private LocalDateTime recallTime;

    @Schema(description = "BPM流程实例ID")
    private String processInstanceId;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
