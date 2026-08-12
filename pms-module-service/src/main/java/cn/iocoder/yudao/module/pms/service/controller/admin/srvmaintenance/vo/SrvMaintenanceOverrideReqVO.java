package cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 维保状态手工覆盖 Request VO")
@Data
public class SrvMaintenanceOverrideReqVO {

    @Schema(description = "维保记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "维保记录编号不能为空")
    private Long id;

    @Schema(description = "维保状态 0未生效 1生效中 2即将过期 3已过期 4已续保", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "维保状态不能为空")
    private Integer maintenanceStatus;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
