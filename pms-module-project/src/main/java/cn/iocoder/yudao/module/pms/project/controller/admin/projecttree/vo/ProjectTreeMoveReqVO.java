package cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 项目树移动 Request VO")
@Data
public class ProjectTreeMoveReqVO {

    @Schema(description = "待移动项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "待移动项目编号不能为空")
    private Long projectId;

    @Schema(description = "目标父项目编号，为 0 表示移到根级", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "目标父项目编号不能为空")
    private Long targetParentId;

    @Schema(description = "移动原因", example = "组织架构调整")
    private String reason;

}
