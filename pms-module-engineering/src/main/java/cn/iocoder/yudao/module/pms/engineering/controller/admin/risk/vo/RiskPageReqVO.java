package cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 单机风险分页 Request VO（FR-ENG-008）。
 */
@Schema(description = "管理后台 - PMS 单机风险分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "风险编号，模糊匹配", example = "RK-2026")
    private String code;

    @Schema(description = "风险名称，模糊匹配", example = "单机故障")
    private String name;

    @Schema(description = "风险类型", example = "SINGLE_DEVICE")
    private String riskType;

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "状态：0 草稿 1 已识别 2 已确认 3 已同步CRM 4 已关闭", example = "0")
    private Integer status;

    @Schema(description = "设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号，模糊匹配", example = "SN001")
    private String deviceSerial;

    @Schema(description = "处理人", example = "1024")
    private Long handlerUserId;

    @Schema(description = "是否已同步CRM", example = "false")
    private Boolean crmSynced;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
