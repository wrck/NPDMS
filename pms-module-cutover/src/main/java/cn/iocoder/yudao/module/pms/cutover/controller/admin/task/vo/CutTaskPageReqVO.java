package cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 割接任务分页 Request VO（FR-CUT-001）。
 */
@Schema(description = "管理后台 - 割接任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutTaskPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "割接任务编码，模糊匹配", example = "CUT2026")
    private String code;

    @Schema(description = "割接任务名称，模糊匹配", example = "核心交换")
    private String name;

    @Schema(description = "当前状态：0草稿 1准备中 2待评审 3闭环中；旧值4至8仅兼容读取", example = "0")
    private Integer status;

    @Schema(description = "割接等级 A/B/C/D", example = "C")
    private String riskLevel;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
