package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 人工确认服务经理 Response VO")
@Data
public class ProjectAssignManagerRespVO {

    @Schema(description = "项目稳定ID")
    private Long projectId;
    @Schema(description = "当前服务经理关系ID")
    private Long assignmentId;
    @Schema(description = "更新后的Project版本")
    private Integer version;
    @Schema(description = "主责指派状态；仅确认服务经理时仍为UNASSIGNED")
    private String assignmentStatus;
}
