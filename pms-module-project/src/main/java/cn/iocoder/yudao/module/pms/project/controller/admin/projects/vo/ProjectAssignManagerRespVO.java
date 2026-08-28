package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 人工确认服务经理 Response VO")
@Data
public class ProjectAssignManagerRespVO {

    @Schema(description = "项目稳定ID")
    private Long projectId;
    @Schema(description = "当前服务经理关系ID")
    private Long assignmentId;
    @Schema(description = "更新后的Project版本")
    private Integer version;
    @Schema(description = "节点指派状态；主责服务经理与项目经理均有效时为ASSIGNED")
    private String assignmentStatus;
    @Schema(description = "服务端事务生效时间")
    private LocalDateTime effectiveFrom;
}
