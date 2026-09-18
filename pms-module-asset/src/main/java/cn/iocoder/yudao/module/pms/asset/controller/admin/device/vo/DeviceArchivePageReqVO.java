package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备档案分页查询 Request VO（ast_device 承载，自 pms_equipment 旧链分页承接）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台 - 设备档案分页查询 Request VO")
public class DeviceArchivePageReqVO extends PageParam {

    @Schema(description = "设备序列号，模糊匹配", example = "SN2026")
    private String sn;

    @Schema(description = "设备名称，模糊匹配", example = "网关")
    private String name;

    @Schema(description = "设备状态", example = "IN_STOCK")
    private String status;

    @Schema(description = "所属项目编号", example = "1")
    private Long projectId;

    @Schema(description = "所属客户编号", example = "1")
    private Long customerId;
}
