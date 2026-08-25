package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理后台 - 设备档案新增/修改 Request VO（FR-RES-001）。
 */
@Schema(description = "管理后台 - 设备档案新增/修改 Request VO")
@Data
public class EquipmentSaveReqVO {

    @Schema(description = "设备编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "全局唯一序列号", requiredMode = Schema.RequiredMode.REQUIRED, example = "SN20260101001")
    @NotBlank(message = "序列号不能为空")
    @Size(max = 128, message = "序列号长度不能超过 128 个字符")
    private String serialNumber;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机")
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "设备型号", example = "S5700-48EI")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String model;

    @Schema(description = "所属客户编号", example = "2048")
    private Long customerId;

    @Schema(description = "所属项目编号", example = "4096")
    private Long projectId;

    @Schema(description = "保修开始日期", example = "2026-01-01")
    private LocalDate warrantyStartDate;

    @Schema(description = "保修结束日期", example = "2028-01-01")
    private LocalDate warrantyEndDate;

    @Schema(description = "备注", example = "客户自购设备")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
