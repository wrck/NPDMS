package cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 施工计划建议规则分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class StageSuggestionRulePageReqVO extends PageParam {

    @Schema(description = "阶段编码", example = "S4")
    private String stageCode;

    @Schema(description = "签约方式", example = "CHANNEL_SIGN")
    private String signingMethod;

    @Schema(description = "建议来源", example = "DURATION_REQUIRE")
    private String sourceType;
}
