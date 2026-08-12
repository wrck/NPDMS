package cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 授权审批 Request VO（FR-ENG-010）。
 * <p>
 * 用于审批动作：通过 / 驳回 / 终止。
 */
@Schema(description = "管理后台 - PMS 授权审批 Request VO")
@Data
public class AuthorizationApproveReqVO {

    @Schema(description = "授权ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "授权ID不能为空")
    private Long id;

    @Schema(description = "审批动作：PASS 通过 / REJECT 驳回 / TERMINATE 终止", requiredMode = Schema.RequiredMode.REQUIRED, example = "PASS")
    @Size(max = 16, message = "审批动作长度不能超过 16 个字符")
    private String approveAction;

    @Schema(description = "审批人", example = "1024")
    private Long approverUserId;

    @Schema(description = "审批意见")
    @Size(max = 500, message = "审批意见长度不能超过 500 个字符")
    private String approveOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
