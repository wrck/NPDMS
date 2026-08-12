package cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 割接风险分页 Request VO（FR-CUT-004）。
 */
@Schema(description = "管理后台 - 割接风险分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutRiskPageReqVO extends PageParam {

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "风险编码，模糊匹配", example = "RSK")
    private String code;

    @Schema(description = "风险名称，模糊匹配", example = "业务中断")
    private String name;

    @Schema(description = "类型 RISK/SURVEY", example = "RISK")
    private String riskType;

    @Schema(description = "状态：0待处理 1处理中 2已闭环 3已挂起", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
