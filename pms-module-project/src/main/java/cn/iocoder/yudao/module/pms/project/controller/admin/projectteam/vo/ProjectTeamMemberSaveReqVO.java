package cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 项目团队成员创建/修改 Request VO")
@Data
public class ProjectTeamMemberSaveReqVO {

    @Schema(description = "团队成员编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户编号不能为空")
    private Long userId;

    @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJECT_MANAGER")
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64 个字符")
    private String roleCode;

    @Schema(description = "角色名称", example = "项目经理")
    @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
    private String roleName;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注", example = "核心成员")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
