package cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 项目阶段完成 Request VO（FR-PROJ-016 / T-V1-PROJ-008）。
 * <p>
 * 调用前需先通过 {@code check-gate} 校验门禁通过；本请求携带门禁证据（评审记录、附件链接等）。
 */
@Schema(description = "管理后台 - 项目阶段完成 Request VO")
@Data
public class ProjectPhaseCompleteReqVO {

    @Schema(description = "阶段编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "阶段编号不能为空")
    private Long phaseId;

    @Schema(description = "门禁证据（评审记录、附件链接等）", example = "需求评审会议纪要 v1.0 已归档")
    private String gateEvidence;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
