package cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 稳定观察分页 Request VO（FR-CUT-013）。
 */
@Schema(description = "管理后台 - 稳定观察分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutObservationPageReqVO extends PageParam {

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "观察编码，模糊匹配", example = "OBS")
    private String code;

    @Schema(description = "状态：0观察中 1已通过 2异常 3已归档", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
