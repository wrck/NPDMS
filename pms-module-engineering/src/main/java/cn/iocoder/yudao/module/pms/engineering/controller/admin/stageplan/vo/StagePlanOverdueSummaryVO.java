package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 超期统计汇总（PLN-03）：与超期阶段清单同口径计算（仅生效计划版本）。
 */
@Schema(description = "管理后台 - 阶段施工计划超期统计汇总")
@Data
public class StagePlanOverdueSummaryVO {

    @Schema(description = "超期项目数（按项目去重）", example = "3")
    private Long overdueProjectCount;

    @Schema(description = "超期阶段数", example = "5")
    private Long overdueStageCount;

}
