package cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 施工计划建议规则 Response VO")
@Data
public class StageSuggestionRuleRespVO {

    @Schema(description = "规则编号", example = "1024")
    private Long id;

    @Schema(description = "阶段编码", example = "S4")
    private String stageCode;

    @Schema(description = "签约方式；空=全部签约方式", example = "CHANNEL_SIGN")
    private String signingMethod;

    @Schema(description = "建议来源", example = "DURATION_REQUIRE")
    private String sourceType;

    @Schema(description = "参照阶段编码", example = "S5")
    private String referenceStageCode;

    @Schema(description = "偏移月数", example = "0")
    private Integer offsetMonths;

    @Schema(description = "偏移天数", example = "-14")
    private Integer offsetDays;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
