package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 现场培训记录分页 Request VO（ACC-01）。
 */
@Schema(description = "管理后台 - 现场培训记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "培训记录编码，模糊匹配", example = "TR-2026")
    private String code;

    @Schema(description = "培训名称，模糊匹配", example = "设备运维培训")
    private String name;

    @Schema(description = "状态：0草稿 1已外发 2客户已确认 3已作废", example = "1")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
