package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 指派服务经理 Request VO（主责改派关闭旧区间，协同追加新区间）
 */
@Schema(description = "管理后台 - 指派服务经理 Request VO")
@Data
public class ProjectAssignManagerReqVO {

    @Schema(description = "服务经理层级：L1/L2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "服务经理层级不能为空")
    private String levelCode;

    @Schema(description = "人员稳定ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long managerId;

    @Schema(description = "实施站点稳定ID；L2必填，L1可为空")
    private Long siteId;

    @Schema(description = "责任类型：PRIMARY/COLLABORATOR", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "责任类型不能为空")
    private String assignmentType;

    @Schema(description = "人工确认的办事处部门ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "办事处部门ID不能为空")
    private Long departmentId;

    @Schema(description = "人工确认的办事处统一部门编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "办事处部门编码不能为空")
    private String departmentCode;

    @Schema(description = "指派或改派原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指派原因不能为空")
    @Size(max = 500, message = "指派原因不能超过500个字符")
    private String changeReason;
}
