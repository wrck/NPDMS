package cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 割接方案分页 Request VO（FR-CUT-008）。
 */
@Schema(description = "管理后台 - 割接方案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutPlanPageReqVO extends PageParam {

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "方案编码，模糊匹配", example = "PLN")
    private String code;

    @Schema(description = "方案名称，模糊匹配", example = "割接")
    private String name;

    @Schema(description = "状态：0草稿 1待评审 2已通过 3已驳回 4已终止", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
