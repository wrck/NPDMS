package cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 团队批量变更明细 Response VO（FR-PROJ-014）。
 */
@Schema(description = "管理后台 - 团队批量变更明细 Response VO")
@Data
public class TeamBatchChangeItemRespVO {

    @Schema(description = "明细编号", example = "1")
    private Long id;

    @Schema(description = "批次编号", example = "1024")
    private Long batchId;

    @Schema(description = "项目编号", example = "100")
    private Long projectId;

    @Schema(description = "项目名称", example = "智慧园区项目")
    private String projectName;

    @Schema(description = "团队成员编号", example = "2048")
    private Long teamMemberId;

    @Schema(description = "变更前角色编码", example = "PROJECT_MANAGER")
    private String beforeRole;

    @Schema(description = "变更后角色编码", example = "PROJECT_MANAGER")
    private String afterRole;

    @Schema(description = "状态：0待处理 1成功 2失败", example = "1")
    private Integer status;

    @Schema(description = "失败原因", example = "目标用户在该项目已存在相同角色")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
