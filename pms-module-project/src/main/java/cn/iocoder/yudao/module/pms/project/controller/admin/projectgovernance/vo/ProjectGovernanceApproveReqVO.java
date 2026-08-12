package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 项目治理动作审批 Request VO")
@Data
public class ProjectGovernanceApproveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "审批动作 PASS 通过执行 / REJECT 驳回 / RETURN 退回", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "审批动作不能为空")
    private String approveAction;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "审批人编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审批人不能为空")
    private Long approverUserId;

}
