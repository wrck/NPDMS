package cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接任务评审动作 Request VO（FR-CUT-009）。
 * <p>
 * 用于 approve / reject 等评审动作，承载评审意见。
 */
@Schema(description = "管理后台 - 割接任务评审动作 Request VO")
@Data
public class CutTaskApproveReqVO {

    @Schema(description = "割接任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "割接任务编号不能为空")
    private Long id;

    @Schema(description = "评审意见", example = "前置门禁已满足，同意推进")
    @Size(max = 500, message = "评审意见长度不能超过 500 个字符")
    private String approvalOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
