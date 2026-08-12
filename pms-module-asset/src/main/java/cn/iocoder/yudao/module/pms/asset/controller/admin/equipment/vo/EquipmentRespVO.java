package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - 设备档案 Response VO（FR-RES-001）。
 */
@Schema(description = "管理后台 - 设备档案 Response VO")
@Data
public class EquipmentRespVO {

    @Schema(description = "设备编号", example = "1024")
    private Long id;

    @Schema(description = "全局唯一序列号", example = "SN20260101001")
    private String serialNumber;

    @Schema(description = "设备名称", example = "核心交换机")
    private String name;

    @Schema(description = "设备型号", example = "S5700-48EI")
    private String model;

    @Schema(description = "所属客户编号", example = "2048")
    private Long customerId;

    @Schema(description = "所属项目编号", example = "4096")
    private Long projectId;

    @Schema(description = "状态：0在库 1在用 2故障 3维修中 4已报废", example = "0")
    private Integer status;

    @Schema(description = "设备位置", example = "机房A-机柜03-U12")
    private String location;

    @Schema(description = "保修开始日期", example = "2026-01-01")
    private LocalDate warrantyStartDate;

    @Schema(description = "保修结束日期", example = "2028-01-01")
    private LocalDate warrantyEndDate;

    @Schema(description = "备注", example = "客户自购设备")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
