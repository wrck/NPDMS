package cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 计划变更审批 Request VO")
@Data
public class PlanChangeApproveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "审批动作 PASS/REJECT/RETURN/TRANSFER/COUNTERSIGN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "审批动作不能为空")
    private String approveAction;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "审批人编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审批人不能为空")
    private Long approverUserId;

}
