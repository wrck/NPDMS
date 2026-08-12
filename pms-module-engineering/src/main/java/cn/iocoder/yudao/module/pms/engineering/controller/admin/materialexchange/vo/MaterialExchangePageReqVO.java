package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 物料换货协同分页 Request VO（FR-ENG-003）。
 */
@Schema(description = "管理后台 - PMS 物料换货协同分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialExchangePageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "换货单号，模糊匹配", example = "ME2026")
    private String code;

    @Schema(description = "换货名称，模糊匹配", example = "核心交换机换货")
    private String name;

    @Schema(description = "换货类型：INCOMPATIBLE 不兼容 / DEFECTIVE 缺陷 / DAMAGED 损坏 / OTHER 其他", example = "INCOMPATIBLE")
    private String exchangeType;

    @Schema(description = "原设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "CRM 推送状态：PENDING 待推送 / SENT 已推送 / RECEIVED 已接收", example = "PENDING")
    private String crmPushStatus;

    @Schema(description = "单据状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "申请人编号", example = "1024")
    private Long applicantUserId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
