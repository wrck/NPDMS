package cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 施工计划建议规则新增/修改 Request VO")
@Data
public class StageSuggestionRuleSaveReqVO {

    @Schema(description = "规则编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "阶段编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "S4")
    @NotBlank(message = "阶段编码不能为空")
    private String stageCode;

    @Schema(description = "签约方式（字典 pms_signing_method）；空=全部签约方式", example = "CHANNEL_SIGN")
    private String signingMethod;

    @Schema(description = "建议来源：PMS_IMPORTED / DURATION_REQUIRE / STAGE_PLAN", requiredMode = Schema.RequiredMode.REQUIRED, example = "DURATION_REQUIRE")
    @NotBlank(message = "建议来源不能为空")
    private String sourceType;

    @Schema(description = "参照阶段编码（STAGE_PLAN 时必填）", example = "S5")
    private String referenceStageCode;

    @Schema(description = "偏移月数（负=提前）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "偏移月数不能为空")
    private Integer offsetMonths;

    @Schema(description = "偏移天数（负=提前，如 -14=2周）", requiredMode = Schema.RequiredMode.REQUIRED, example = "-14")
    @NotNull(message = "偏移天数不能为空")
    private Integer offsetDays;

    @Schema(description = "备注", example = "Demo 割接-上线=工期时间-2周")
    private String remark;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}
