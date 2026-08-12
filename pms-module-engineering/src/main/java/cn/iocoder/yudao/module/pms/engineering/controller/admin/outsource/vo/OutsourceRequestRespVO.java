package cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 外包申请 Response VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS 外包申请 Response VO")
@Data
public class OutsourceRequestRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "外包单号", example = "OS20260101001")
    private String code;

    @Schema(description = "外包名称", example = "驻场运维服务")
    private String name;

    @Schema(description = "外包类型", example = "LABOR")
    private String outsourceType;

    @Schema(description = "工作内容", example = "负责核心交换机日常巡检")
    private String workContent;

    @Schema(description = "工作量", example = "120.50")
    private BigDecimal workQuantity;

    @Schema(description = "工作量单位", example = "人日")
    private String workUnit;

    @Schema(description = "计划开始日期", example = "2026-01-01")
    private LocalDate plannedStartDate;

    @Schema(description = "计划结束日期", example = "2026-06-30")
    private LocalDate plannedEndDate;

    @Schema(description = "预估成本", example = "50000.00")
    private BigDecimal estimatedCost;

    @Schema(description = "实际成本", example = "48000.00")
    private BigDecimal actualCost;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "供应商编号", example = "1024")
    private Long vendorId;

    @Schema(description = "供应商名称", example = "某技术服务有限公司")
    private String vendorName;

    @Schema(description = "联系人编号", example = "1024")
    private Long contactUserId;

    @Schema(description = "联系电话", example = "13800000000")
    private String contactPhone;

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

    @Schema(description = "备注", example = "需提前一周入场")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
