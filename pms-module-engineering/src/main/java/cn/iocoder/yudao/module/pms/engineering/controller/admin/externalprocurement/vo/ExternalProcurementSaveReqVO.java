package cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 外采申请新增/修改 Request VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS 外采申请新增/修改 Request VO")
@Data
public class ExternalProcurementSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "外采单号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "EP20260101001")
    @NotBlank(message = "外采单号不能为空")
    @Size(max = 64, message = "外采单号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "外采名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机采购")
    @NotBlank(message = "外采名称不能为空")
    @Size(max = 200, message = "外采名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "外采类型：GOODS 物资 / SERVICE 服务 / OTHER 其他", example = "GOODS")
    @Size(max = 32, message = "外采类型长度不能超过 32 个字符")
    private String procurementType;

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

    @Schema(description = "品牌", example = "华为")
    @Size(max = 100, message = "品牌长度不能超过 100 个字符")
    private String brand;

    @Schema(description = "型号", example = "S9300")
    @Size(max = 100, message = "型号长度不能超过 100 个字符")
    private String model;

    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "2.00")
    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;

    @Schema(description = "单位，默认 个", example = "台")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "单价", example = "50000.00")
    private BigDecimal unitPrice;

    @Schema(description = "总价", example = "100000.00")
    private BigDecimal totalPrice;

    @Schema(description = "币种，默认 CNY", example = "CNY")
    @Size(max = 8, message = "币种长度不能超过 8 个字符")
    private String currency;

    @Schema(description = "供应商名称", example = "某科技发展有限公司")
    @Size(max = 200, message = "供应商名称长度不能超过 200 个字符")
    private String supplierName;

    @Schema(description = "供应商联系人", example = "张三")
    @Size(max = 100, message = "供应商联系人长度不能超过 100 个字符")
    private String supplierContact;

    @Schema(description = "供应商联系电话", example = "13800000000")
    @Size(max = 64, message = "供应商联系电话长度不能超过 64 个字符")
    private String supplierPhone;

    @Schema(description = "需求日期", example = "2026-01-15")
    private LocalDate neededDate;

    @Schema(description = "期望交付日期", example = "2026-02-15")
    private LocalDate expectedDeliveryDate;

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

    @Schema(description = "备注", example = "需附带原厂授权函")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
