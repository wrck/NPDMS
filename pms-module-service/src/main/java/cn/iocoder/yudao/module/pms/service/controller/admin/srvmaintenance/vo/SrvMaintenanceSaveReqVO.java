package cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 维保记录创建/修改 Request VO")
@Data
public class SrvMaintenanceSaveReqVO {

    @Schema(description = "维保记录编号", example = "1024")
    private Long id;

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    @NotNull(message = "设备编号不能为空")
    private Long equipmentId;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "维保记录编码，设备内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "M-001")
    @NotBlank(message = "维保记录编码不能为空")
    @Size(max = 64, message = "维保记录编码长度不能超过 64 个字符")
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

}
