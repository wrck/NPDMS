package cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - PMS 项目任务移动 Request VO")
@Data
public class ProjectTaskMoveReqVO {

    @Schema(description = "待移动任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "待移动任务编号不能为空")
    private Long taskId;

    @Schema(description = "目标父任务编号，为空或 0 表示移到根级", example = "0")
    private Long targetParentId;

}
