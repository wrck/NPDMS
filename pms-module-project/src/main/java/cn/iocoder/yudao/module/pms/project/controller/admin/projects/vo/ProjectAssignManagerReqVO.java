package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指派一级服务经理 Request VO（旧区间关闭+新区间开启，留痕前后值）
 */
@Schema(description = "管理后台 - 指派一级服务经理 Request VO")
@Data
public class ProjectAssignManagerReqVO {

    @Schema(description = "角色编码，固定SERVICE_MANAGER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @Schema(description = "服务经理层级：L1/L2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "服务经理层级不能为空")
    private String levelCode;

    @Schema(description = "人员稳定ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long managerId;

    @Schema(description = "实施站点稳定ID；仅站点未维护的UNRESOLVED项目可为空")
    private Long siteId;

    @Schema(description = "人工确认的办事处统一部门编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "办事处部门编码不能为空")
    private String departmentCode;

    @Schema(description = "生效开始时间（空=当前时间，不得晚于当前时间）")
    private LocalDateTime effectiveFrom;
}
