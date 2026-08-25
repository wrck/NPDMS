package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目任务指派 Request VO")
@Data
public class ProjectTaskAssignReqVO {

    @Schema(description = "负责人用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "负责人用户ID不能为空")
    private Long assigneeUserId;

    @Schema(description = "指派或转派原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指派原因不能为空")
    @Size(max = 500, message = "指派原因不能超过500个字符")
    private String reason;
}
