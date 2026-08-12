package cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 计划变更 Response VO")
@Data
public class PlanChangeRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属项目编号")
    private Long projectId;

    @Schema(description = "变更单号")
    private String changeNo;

    @Schema(description = "变更标题")
    private String title;

    @Schema(description = "变更类型")
    private String changeType;

    @Schema(description = "变更原因")
    private String reason;

    @Schema(description = "客户证明材料文件URL列表（JSON数组）")
    private String customerProofFiles;

    @Schema(description = "申请人编号")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "审批人编号")
    private Long approverUserId;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "审批动作")
    private String approveAction;

    @Schema(description = "当前基线版本号")
    private Integer baselineVersion;

    @Schema(description = "新基线版本号")
    private Integer newBaselineVersion;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
