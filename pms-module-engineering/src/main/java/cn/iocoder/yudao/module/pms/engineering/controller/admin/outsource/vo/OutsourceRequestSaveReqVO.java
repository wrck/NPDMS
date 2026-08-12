package cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 外包申请新增/修改 Request VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS 外包申请新增/修改 Request VO")
@Data
public class OutsourceRequestSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "外包单号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "OS20260101001")
    @NotBlank(message = "外包单号不能为空")
    @Size(max = 64, message = "外包单号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "外包名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "驻场运维服务")
    @NotBlank(message = "外包名称不能为空")
    @Size(max = 200, message = "外包名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "外包类型：LABOR 劳务 / SERVICE 服务 / OTHER 其他", example = "LABOR")
    @Size(max = 32, message = "外包类型长度不能超过 32 个字符")
    private String outsourceType;

    @Schema(description = "工作内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "负责核心交换机日常巡检")
    @NotBlank(message = "工作内容不能为空")
    private String workContent;

    @Schema(description = "工作量", example = "120.50")
    private BigDecimal workQuantity;

    @Schema(description = "工作量单位", example = "人日")
    @Size(max = 32, message = "工作量单位长度不能超过 32 个字符")
    private String workUnit;

    @Schema(description = "计划开始日期", example = "2026-01-01")
    private LocalDate plannedStartDate;

    @Schema(description = "计划结束日期", example = "2026-06-30")
    private LocalDate plannedEndDate;

    @Schema(description = "预估成本", example = "50000.00")
    private BigDecimal estimatedCost;

    @Schema(description = "实际成本", example = "48000.00")
    private BigDecimal actualCost;

    @Schema(description = "币种，默认 CNY", example = "CNY")
    @Size(max = 8, message = "币种长度不能超过 8 个字符")
    private String currency;

    @Schema(description = "供应商编号", example = "1024")
    private Long vendorId;

    @Schema(description = "供应商名称", example = "某技术服务有限公司")
    @Size(max = 200, message = "供应商名称长度不能超过 200 个字符")
    private String vendorName;

    @Schema(description = "联系人编号", example = "1024")
    private Long contactUserId;

    @Schema(description = "联系电话", example = "13800000000")
    @Size(max = 64, message = "联系电话长度不能超过 64 个字符")
    private String contactPhone;

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

    @Schema(description = "备注", example = "需提前一周入场")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
