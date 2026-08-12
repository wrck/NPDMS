package cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接方案评审动作 Request VO（FR-CUT-009）。
 */
@Schema(description = "管理后台 - 割接方案评审动作 Request VO")
@Data
public class CutPlanApproveReqVO {

    @Schema(description = "方案编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "方案编号不能为空")
    private Long id;

    @Schema(description = "审核意见", example = "方案完整可执行，同意推进")
    @Size(max = 500, message = "审核意见长度不能超过 500 个字符")
    private String approvalOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
