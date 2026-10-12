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

    @Schema(description = "成员区间的原人员编码")
    private String memberCode;

    @Schema(description = "成员区间自身的联系电话")
    private String phoneNumber;

    @Schema(description = "原登记来源：1项目信息，2手工成员")
    private String registrationSource;

    @Schema(description = "是否经Owner确认参与当前项目授权")
    private Boolean authorizationConfirmed;

    @Schema(description = "System人员主档中的当前联系电话")
    private String mobile;

    @Schema(description = "历史成员使用区间邮箱，正常成员使用当前人员邮箱")
    private String email;

    @Schema(description = "成员角色（字典 pms_project_member_role）")
    private String memberRole;

    private String assignmentType;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private Long siteId;
    private String responsibility;
    private String remark;
    private String changeReason;
    private String endReason;

    @Schema(description = "生效开始时间")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间（NULL=当前有效）")
    private LocalDateTime effectiveTo;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
