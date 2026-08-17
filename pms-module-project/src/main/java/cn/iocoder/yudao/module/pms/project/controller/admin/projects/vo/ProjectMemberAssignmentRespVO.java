package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员区间 Response VO（当前有效+历史，区间留痕）
 */
@Schema(description = "管理后台 - 项目成员区间 Response VO")
@Data
public class ProjectMemberAssignmentRespVO {

    @Schema(description = "成员区间ID")
    private Long id;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "成员工号")
    private String employeeNo;

    @Schema(description = "成员姓名")
    private String memberName;

    @Schema(description = "成员角色（字典 pms_project_member_role）")
    private String memberRole;

    @Schema(description = "生效开始时间")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间（NULL=当前有效）")
    private LocalDateTime effectiveTo;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
