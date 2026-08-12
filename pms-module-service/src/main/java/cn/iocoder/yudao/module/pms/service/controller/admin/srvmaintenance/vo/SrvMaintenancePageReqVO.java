package cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 维保记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvMaintenancePageReqVO extends PageParam {

    @Schema(description = "设备编号", example = "200")
    private Long equipmentId;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "维保记录编码，模糊匹配", example = "M-001")
    private String code;

    @Schema(description = "维保状态 0未生效 1生效中 2即将过期 3已过期 4已续保", example = "0")
    private Integer maintenanceStatus;

    @Schema(description = "服务等级", example = "GOLD")
    private String serviceLevel;

    @Schema(description = "是否自动计算", example = "true")
    private Boolean autoCalculated;

    @Schema(description = "是否手工覆盖", example = "false")
    private Boolean manualOverride;

}
