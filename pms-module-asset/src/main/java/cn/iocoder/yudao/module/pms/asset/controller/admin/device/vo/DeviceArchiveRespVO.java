package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备档案 Response VO（ast_device 承载，自 pms_equipment 旧链 RespVO 承接）。
 * <p>
 * 位置文本不再作为独立档案字段：ast_device 以 siteId/siteLocationId/locationResolutionStatus/
 * locationSnapshot/locationEffectiveFrom 表达当前位置，由安装完成动作生效。
 */
@Data
@Schema(description = "管理后台 - 设备档案 Response VO")
public class DeviceArchiveRespVO {

    @Schema(description = "设备编号", example = "1")
    private Long id;

    @Schema(description = "设备序列号", example = "SN2026001")
    private String sn;

    @Schema(description = "设备名称", example = "边缘网关")
    private String name;

    @Schema(description = "设备型号", example = "GW-100")
    private String productModel;

    @Schema(description = "设备状态", example = "IN_STOCK")
    private String status;

    @Schema(description = "所属客户编号", example = "1")
    private Long customerId;

    @Schema(description = "所属项目编号", example = "1")
    private Long projectId;

    @Schema(description = "站点编号", example = "1")
    private Long siteId;

    @Schema(description = "站点内部位置编号", example = "1")
    private Long siteLocationId;

    @Schema(description = "位置解析状态：RESOLVED/UNRESOLVED", example = "RESOLVED")
    private String locationResolutionStatus;

    @Schema(description = "位置快照")
    private String locationSnapshot;

    @Schema(description = "位置生效时间")
    private LocalDateTime locationEffectiveFrom;

    @Schema(description = "位置记录编号", example = "1")
    private Long locationRecordId;

    @Schema(description = "保修开始日期")
    private LocalDate warrantyStartDate;

    @Schema(description = "保修结束日期")
    private LocalDate warrantyEndDate;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本", example = "1")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
