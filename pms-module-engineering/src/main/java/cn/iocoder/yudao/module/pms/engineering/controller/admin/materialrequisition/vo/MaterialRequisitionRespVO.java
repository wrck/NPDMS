package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS OA领料申请 Response VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS OA领料申请 Response VO")
@Data
public class MaterialRequisitionRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "领料单号", example = "MR20260101001")
    private String code;

    @Schema(description = "领料名称", example = "10G 光模块领料")
    private String name;

    @Schema(description = "领料类型", example = "SPARE")
    private String requisitionType;

    @Schema(description = "关联设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "物料名称", example = "10G 光模块")
    private String materialName;

    @Schema(description = "物料编码", example = "MAT-001")
    private String materialCode;

    @Schema(description = "规格型号", example = "SFP+ 10G LR")
    private String specification;

    @Schema(description = "数量", example = "10.00")
    private BigDecimal quantity;

    @Schema(description = "单位", example = "个")
    private String unit;

    @Schema(description = "需求日期", example = "2026-01-15")
    private LocalDate neededDate;

    @Schema(description = "仓库编号", example = "1024")
    private Long warehouseId;

    @Schema(description = "仓库名称", example = "北京中心库")
    private String warehouseName;

    @Schema(description = "库存状态", example = "IN_STOCK")
    private String stockStatus;

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

    @Schema(description = "备注", example = "需提前一周备货")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
