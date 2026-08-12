package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 设备状态变更 Request VO（FR-RES-001）。
 * <p>
 * action 取值与设备版本历史 change_type 对齐：
 * DEPLOY / REPORT_FAULT / START_REPAIR / COMPLETE_REPAIR / SCRAP。
 * COMPLETE_REPAIR 可通过 targetStatus 指定回库后在库(0)或在用(1)，默认在用。
 */
@Schema(description = "管理后台 - 设备状态变更 Request VO")
@Data
public class EquipmentStatusChangeReqVO {

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "设备编号不能为空")
    private Long id;

    @Schema(description = "状态变更动作：DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "DEPLOY")
    @NotBlank(message = "操作类型不能为空")
    private String action;

    @Schema(description = "目标状态（仅 COMPLETE_REPAIR 使用，0在库/1在用，默认在用）", example = "1")
    private Integer targetStatus;

    @Schema(description = "变更描述", example = "部署至客户机房")
    @Size(max = 500, message = "变更描述长度不能超过 500 个字符")
    private String changeDescription;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
