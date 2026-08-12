package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS OA领料申请审批 Request VO（FR-ENG-002）。
 * <p>
 * 用于 approve 接口。审批动作 approveAction 决定状态流转：
 * PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签。
 */
@Schema(description = "管理后台 - PMS OA领料申请审批 Request VO")
@Data
public class MaterialRequisitionApproveReqVO {

    @Schema(description = "领料申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "领料申请编号不能为空")
    private Long id;

    @Schema(description = "审批动作：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "PASS")
    @NotBlank(message = "审批动作不能为空")
    @Size(max = 32, message = "审批动作长度不能超过 32 个字符")
    private String approveAction;

    @Schema(description = "审批人编号", example = "1024")
    private Long approverUserId;

    @Schema(description = "审批意见", example = "同意")
    @Size(max = 1000, message = "审批意见长度不能超过 1000 个字符")
    private String approveOpinion;
}
