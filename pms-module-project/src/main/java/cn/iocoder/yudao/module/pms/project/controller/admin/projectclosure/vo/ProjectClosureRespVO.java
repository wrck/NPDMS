package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目闭环 Response VO")
@Data
public class ProjectClosureRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "闭环编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "CL-001")
    private String code;

    @Schema(description = "闭环名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目闭环")
    private String name;

    @Schema(description = "闭环类型 NORMAL 正常闭环 / CONDITIONAL 带条件移交", example = "NORMAL")
    private String closureType;

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

    @Schema(description = "遗留问题摘要")
    private String legacyIssueSummary;

    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;

    @Schema(description = "状态 0草稿 1待审批 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
