package cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 初验/终验 Response VO")
@Data
@Deprecated(since = "F-ACC-001", forRemoval = false)
public class AcceptanceRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "验收编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACC-001")
    private String code;

    @Schema(description = "验收名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目初验")
    private String name;

    @Schema(description = "验收类型 PRELIMINARY 初验 / FINAL 终验", example = "PRELIMINARY")
    private String acceptanceType;

    @Schema(description = "验收日期")
    private LocalDate acceptanceDate;

    @Schema(description = "关联交付计划编号", example = "300")
    private Long planId;

    @Schema(description = "申请人", example = "500")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "审批人", example = "501")
    private Long approverUserId;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;

    @Schema(description = "状态 0草稿 1待提交 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
