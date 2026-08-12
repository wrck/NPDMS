package cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 单机风险确认/处理 Request VO（FR-ENG-008）。
 * <p>
 * 用于风险状态推进：已识别 → 已确认 / 已确认 → 已关闭。
 */
@Schema(description = "管理后台 - PMS 单机风险确认/处理 Request VO")
@Data
public class RiskHandleReqVO {

    @Schema(description = "风险ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "风险ID不能为空")
    private Long id;

    @Schema(description = "处理人", example = "1024")
    private Long handlerUserId;

    @Schema(description = "处理意见")
    @Size(max = 500, message = "处理意见长度不能超过 500 个字符")
    private String handleOpinion;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
