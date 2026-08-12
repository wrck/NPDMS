package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS OA领料申请新增/修改 Request VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS OA领料申请新增/修改 Request VO")
@Data
public class MaterialRequisitionSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "领料单号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "MR20260101001")
    @NotBlank(message = "领料单号不能为空")
    @Size(max = 64, message = "领料单号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "领料名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "10G 光模块领料")
    @NotBlank(message = "领料名称不能为空")
    @Size(max = 200, message = "领料名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "领料类型：SPARE 备件 / CONSUMABLE 耗材 / TOOL 工具 / OTHER 其他", example = "SPARE")
    @Size(max = 32, message = "领料类型长度不能超过 32 个字符")
    private String requisitionType;

    @Schema(description = "关联设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "物料名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "10G 光模块")
    @NotBlank(message = "物料名称不能为空")
    @Size(max = 200, message = "物料名称长度不能超过 200 个字符")
    private String materialName;

    @Schema(description = "物料编码", example = "MAT-001")
    @Size(max = 64, message = "物料编码长度不能超过 64 个字符")
    private String materialCode;

    @Schema(description = "规格型号", example = "SFP+ 10G LR")
    @Size(max = 200, message = "规格型号长度不能超过 200 个字符")
    private String specification;

    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10.00")
    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;

    @Schema(description = "单位，默认 个", example = "个")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "需求日期", example = "2026-01-15")
    private LocalDate neededDate;

    @Schema(description = "仓库编号", example = "1024")
    private Long warehouseId;

    @Schema(description = "仓库名称", example = "北京中心库")
    @Size(max = 200, message = "仓库名称长度不能超过 200 个字符")
    private String warehouseName;

    @Schema(description = "库存状态：IN_STOCK 有库存 / OUT_OF_STOCK 无库存 / RESERVED 已预留", example = "IN_STOCK")
    @Size(max = 32, message = "库存状态长度不能超过 32 个字符")
    private String stockStatus;

    @Schema(description = "附件文件")
    @Size(max = 2000, message = "附件文件长度不能超过 2000 个字符")
    private String attachmentFiles;

    @Schema(description = "触发来源：MANUAL 手动 / WBS 任务触发 / ISSUE 问题触发", example = "MANUAL")
    @Size(max = 32, message = "触发来源长度不能超过 32 个字符")
    private String triggerSource;

    @Schema(description = "触发来源关联编号", example = "1024")
    private Long triggerRefId;

    @Schema(description = "申请人编号", example = "1024")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "备注", example = "需提前一周备货")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
