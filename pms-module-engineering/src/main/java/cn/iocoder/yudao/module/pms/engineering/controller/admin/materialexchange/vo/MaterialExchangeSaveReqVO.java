package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 物料换货协同新增/修改 Request VO（FR-ENG-003）。
 */
@Schema(description = "管理后台 - PMS 物料换货协同新增/修改 Request VO")
@Data
public class MaterialExchangeSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "换货单号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "ME20260101001")
    @NotBlank(message = "换货单号不能为空")
    @Size(max = 64, message = "换货单号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "换货名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机换货")
    @NotBlank(message = "换货名称不能为空")
    @Size(max = 200, message = "换货名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "换货类型：INCOMPATIBLE 不兼容 / DEFECTIVE 缺陷 / DAMAGED 损坏 / OTHER 其他", example = "INCOMPATIBLE")
    @Size(max = 32, message = "换货类型长度不能超过 32 个字符")
    private String exchangeType;

    @Schema(description = "原设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "物料名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机")
    @NotBlank(message = "物料名称不能为空")
    @Size(max = 200, message = "物料名称长度不能超过 200 个字符")
    private String materialName;

    @Schema(description = "物料编码", example = "MAT-S9300")
    @Size(max = 64, message = "物料编码长度不能超过 64 个字符")
    private String materialCode;

    @Schema(description = "规格型号描述", example = "48口千兆光交换")
    @Size(max = 200, message = "规格长度不能超过 200 个字符")
    private String specification;

    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.00")
    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;

    @Schema(description = "单位，默认 个", example = "台")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "原订单号", example = "PO20260101001")
    @Size(max = 64, message = "原订单号长度不能超过 64 个字符")
    private String originalOrderNo;

    @Schema(description = "换货原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "到货设备与设计方案不兼容")
    @NotBlank(message = "换货原因不能为空")
    private String reason;

    @Schema(description = "原因附件文件")
    @Size(max = 2000, message = "原因附件文件长度不能超过 2000 个字符")
    private String reasonFiles;

    @Schema(description = "申请人编号", example = "1024")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "备注", example = "需供应商协同确认")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
