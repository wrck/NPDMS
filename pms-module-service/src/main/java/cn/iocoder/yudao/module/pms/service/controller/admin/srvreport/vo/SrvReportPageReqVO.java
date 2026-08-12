package cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 巡检报告分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvReportPageReqVO extends PageParam {

    @Schema(description = "所属巡检任务编号", example = "100")
    private Long taskId;

    @Schema(description = "报告编码，模糊匹配", example = "RPT-001")
    private String code;

    @Schema(description = "报告类型 STANDARD 标准 / PDF / DOC / XML", example = "STANDARD")
    private String reportType;

    @Schema(description = "状态 0草稿 1已生成 2已归档", example = "0")
    private Integer status;

    @Schema(description = "生成人", example = "300")
    private Long generatedBy;

    @Schema(description = "生成时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] generatedTime;

}
