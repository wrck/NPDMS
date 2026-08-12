package cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 实施问题验证/复测 Request VO（FR-ENG-026）。
 * <p>
 * 关闭（close）动作需提供复测结果；驳回（reject）动作需提供驳回原因。
 */
@Schema(description = "管理后台 - 实施问题验证/复测 Request VO")
@Data
public class IssueVerifyReqVO {

    @Schema(description = "问题编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "问题编号不能为空")
    private Long id;

    @Schema(description = "复测结果（close 必填）", example = "切换耗时 45s，业务无中断")
    @Size(max = 2000, message = "复测结果长度不能超过 2000 个字符")
    private String verifyResult;

    @Schema(description = "验证人编号", example = "1024")
    private Long verifiedBy;

    @Schema(description = "驳回原因（reject 必填）", example = "切换耗时仍超过 60s，需重新整改")
    @Size(max = 1000, message = "驳回原因长度不能超过 1000 个字符")
    private String rejectReason;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @NotBlank(message = "操作类型不能为空")
    private String action;
}
