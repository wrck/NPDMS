package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 物料换货协同 Response VO（FR-ENG-003）。
 */
@Schema(description = "管理后台 - PMS 物料换货协同 Response VO")
@Data
public class MaterialExchangeRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "换货单号", example = "ME20260101001")
    private String code;

    @Schema(description = "换货名称", example = "核心交换机换货")
    private String name;

    @Schema(description = "换货类型", example = "INCOMPATIBLE")
    private String exchangeType;

    @Schema(description = "原设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "物料名称", example = "核心交换机")
    private String materialName;

    @Schema(description = "物料编码", example = "MAT-S9300")
    private String materialCode;

    @Schema(description = "规格型号描述", example = "48口千兆光交换")
    private String specification;

    @Schema(description = "数量", example = "1.00")
    private BigDecimal quantity;

    @Schema(description = "单位", example = "台")
    private String unit;

    @Schema(description = "原订单号", example = "PO20260101001")
    private String originalOrderNo;

    @Schema(description = "换货原因", example = "到货设备与设计方案不兼容")
    private String reason;

    @Schema(description = "原因附件文件")
    private String reasonFiles;

    @Schema(description = "CRM 推送状态：PENDING / SENT / RECEIVED", example = "PENDING")
    private String crmPushStatus;

    @Schema(description = "CRM 推送时间")
    private LocalDateTime crmPushTime;

    @Schema(description = "CRM 工单号", example = "CRM-1024")
    private String crmOrderNo;

    @Schema(description = "新设备编号（换货后设备）", example = "2048")
    private Long newEquipmentId;

    @Schema(description = "换货进度描述")
    private String exchangeProgress;

    @Schema(description = "申请人编号", example = "1024")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "审批人编号", example = "1024")
    private Long approverUserId;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "审批动作", example = "PASS")
    private String approveAction;

    @Schema(description = "单据状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "需供应商协同确认")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
