package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 设备档案分页 Request VO（FR-RES-001）。
 */
@Schema(description = "管理后台 - 设备档案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentPageReqVO extends PageParam {

    @Schema(description = "全局唯一序列号", example = "SN2026")
    private String serialNumber;

    @Schema(description = "设备名称，模糊匹配", example = "交换机")
    private String name;

    @Schema(description = "设备型号", example = "S5700")
    private String model;

    @Schema(description = "所属客户编号", example = "2048")
    private Long customerId;

    @Schema(description = "所属项目编号", example = "4096")
    private Long projectId;

    @Schema(description = "状态：0在库 1在用 2故障 3维修中 4已报废", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
