package cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 巡检执行记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvExecutionPageReqVO extends PageParam {

    @Schema(description = "所属巡检任务编号", example = "100")
    private Long taskId;

    @Schema(description = "执行编码，模糊匹配", example = "E-001")
    private String code;

    @Schema(description = "关联规则编号", example = "200")
    private Long ruleId;

    @Schema(description = "执行人", example = "300")
    private Long executorUserId;

    @Schema(description = "状态 0待执行 1执行中 2已完成 3异常", example = "0")
    private Integer status;

    @Schema(description = "执行时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] executionTime;

}
