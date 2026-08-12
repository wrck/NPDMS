package cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 准备数据表单实例审核 Request VO（FR-ENG-007）。
 * <p>
 * 审核动作：PASS 通过 / REJECT 驳回
 */
@Schema(description = "管理后台 - PMS 准备数据表单实例审核 Request VO")
@Data
public class FormInstanceApproveReqVO {

    @Schema(description = "实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "实例编号不能为空")
    private Long id;

    @Schema(description = "审核动作：PASS 通过 / REJECT 驳回", requiredMode = Schema.RequiredMode.REQUIRED, example = "PASS")
    @NotBlank(message = "审核动作不能为空")
    @Size(max = 32, message = "审核动作长度不能超过 32 个字符")
    private String approveAction;

    @Schema(description = "审核人", example = "1024")
    private Long approverUserId;

    @Schema(description = "审核意见", example = "数据完整，审核通过")
    @Size(max = 500, message = "审核意见长度不能超过 500 个字符")
    private String approveOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
