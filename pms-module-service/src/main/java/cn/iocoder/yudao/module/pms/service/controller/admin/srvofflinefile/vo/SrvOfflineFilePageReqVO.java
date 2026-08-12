package cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 离线巡检文件分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvOfflineFilePageReqVO extends PageParam {

    @Schema(description = "所属巡检任务编号", example = "100")
    private Long taskId;

    @Schema(description = "文件编码，模糊匹配", example = "F-001")
    private String code;

    @Schema(description = "解析状态 0待解析 1解析中 2解析成功 3解析失败", example = "0")
    private Integer parseStatus;

    @Schema(description = "解析人", example = "300")
    private Long parsedBy;

    @Schema(description = "解析时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] parsedTime;

}
