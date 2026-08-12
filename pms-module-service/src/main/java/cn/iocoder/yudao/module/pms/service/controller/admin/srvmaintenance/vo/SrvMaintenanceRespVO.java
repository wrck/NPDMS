package cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 维保记录 Response VO")
@Data
public class SrvMaintenanceRespVO {

    @Schema(description = "维保记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    private Long equipmentId;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "维保记录编码，设备内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "M-001")
    private String code;

    @Schema(description = "维保开始日期")
    private LocalDate startDate;

    @Schema(description = "维保结束日期")
    private LocalDate endDate;

    @Schema(description = "维保状态 0未生效 1生效中 2即将过期 3已过期 4已续保", example = "0")
    private Integer maintenanceStatus;

    @Schema(description = "服务等级", example = "GOLD")
    private String serviceLevel;

    @Schema(description = "是否自动计算", example = "true")
    private Boolean autoCalculated;

    @Schema(description = "是否手工覆盖", example = "false")
    private Boolean manualOverride;

    @Schema(description = "覆盖人", example = "300")
    private Long overrideBy;

    @Schema(description = "覆盖时间")
    private LocalDateTime overrideTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
