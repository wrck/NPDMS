package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 巡检问题分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvIssuePageReqVO extends PageParam {

    @Schema(description = "所属巡检任务编号", example = "100")
    private Long taskId;

    @Schema(description = "问题编码，模糊匹配", example = "ISS-001")
    private String code;

    @Schema(description = "问题名称，模糊匹配", example = "端口")
    private String name;

    @Schema(description = "严重程度 H 高 / M 中 / L 低", example = "M")
    private String severity;

    @Schema(description = "责任人", example = "300")
    private Long ownerUserId;

    @Schema(description = "状态 0待分派 1已分派 2待验证 3已关闭 4已取消", example = "0")
    private Integer status;

    @Schema(description = "整改截止时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] deadline;

}
