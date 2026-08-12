package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 实施方案审批 Request VO（FR-ENG-013）。
 * <p>
 * 用于 approve / reject 接口。
 */
@Schema(description = "管理后台 - 实施方案审批 Request VO")
@Data
public class SolutionApproveReqVO {

    @Schema(description = "方案编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "方案编号不能为空")
    private Long id;

    @Schema(description = "审核意见")
    private String approvalOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
