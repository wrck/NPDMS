package cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 外采申请 Response VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS 外采申请 Response VO")
@Data
public class ExternalProcurementRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "外采单号", example = "EP20260101001")
    private String code;

    @Schema(description = "外采名称", example = "核心交换机采购")
    private String name;

    @Schema(description = "外采类型", example = "GOODS")
    private String procurementType;

    @Schema(description = "物料名称", example = "核心交换机")
    private String materialName;

    @Schema(description = "物料编码", example = "MAT-S9300")
    private String materialCode;

    @Schema(description = "规格型号描述", example = "48口千兆光交换")
    private String specification;

    @Schema(description = "品牌", example = "华为")
    private String brand;

    @Schema(description = "型号", example = "S9300")
    private String model;

    @Schema(description = "数量", example = "2.00")
    private BigDecimal quantity;

    @Schema(description = "单位", example = "台")
    private String unit;

    @Schema(description = "单价", example = "50000.00")
    private BigDecimal unitPrice;

    @Schema(description = "总价", example = "100000.00")
    private BigDecimal totalPrice;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "供应商名称", example = "某科技发展有限公司")
    private String supplierName;

    @Schema(description = "供应商联系人", example = "张三")
    private String supplierContact;

    @Schema(description = "供应商联系电话", example = "13800000000")
    private String supplierPhone;

    @Schema(description = "需求日期", example = "2026-01-15")
    private LocalDate neededDate;

    @Schema(description = "期望交付日期", example = "2026-02-15")
    private LocalDate expectedDeliveryDate;

    @Schema(description = "附件文件")
    private String attachmentFiles;

    @Schema(description = "触发来源", example = "MANUAL")
    private String triggerSource;

    @Schema(description = "触发来源关联编号", example = "1024")
    private Long triggerRefId;

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

    @Schema(description = "BPM 流程实例编号", example = "PI-1024")
    private String bpmProcessInstanceId;

    @Schema(description = "状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "需附带原厂授权函")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
