package cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 巡检任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvTaskPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "设备编号", example = "200")
    private Long equipmentId;

    @Schema(description = "巡检任务编码，模糊匹配", example = "T-001")
    private String code;

    @Schema(description = "巡检任务名称，模糊匹配", example = "交换机")
    private String name;

    @Schema(description = "巡检方式 ONLINE 在线 / OFFLINE 离线", example = "ONLINE")
    private String inspectionMode;

    @Schema(description = "来源 PROJECT 项目 / PLAN 服务计划 / MANUAL 手工", example = "MANUAL")
    private String sourceType;

    @Schema(description = "状态 0草稿 1待执行 2执行中 3待确认 4已完成 5已取消", example = "0")
    private Integer status;

    @Schema(description = "计划巡检时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] scheduledTime;

    @Schema(description = "实际巡检时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] actualTime;

}
