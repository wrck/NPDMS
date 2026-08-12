package cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 割接执行分页 Request VO（FR-CUT-011）。
 */
@Schema(description = "管理后台 - 割接执行分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutExecutionPageReqVO extends PageParam {

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "执行编码，模糊匹配", example = "EXE")
    private String code;

    @Schema(description = "步骤名称，模糊匹配", example = "主用下线")
    private String stepName;

    @Schema(description = "状态：0待执行 1执行中 2已通过 3失败 4已回退", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
