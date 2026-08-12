package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 设备版本历史 Response VO（FR-RES-002，追加只读）。
 */
@Schema(description = "管理后台 - 设备版本历史 Response VO")
@Data
public class EquipmentVersionRespVO {

    @Schema(description = "版本记录编号", example = "1024")
    private Long id;

    @Schema(description = "设备编号", example = "2048")
    private Long equipmentId;

    @Schema(description = "版本号（按设备递增）", example = "1")
    private Integer versionNo;

    @Schema(description = "变更类型：CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP",
            example = "CREATE")
    private String changeType;

    @Schema(description = "变更描述", example = "创建设备档案")
    private String changeDescription;

    @Schema(description = "变更前快照(JSON)")
    private String beforeSnapshot;

    @Schema(description = "变更后快照(JSON)")
    private String afterSnapshot;

    @Schema(description = "创建者", example = "admin")
    private String creator;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
