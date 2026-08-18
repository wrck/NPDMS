package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 子树移动 Request VO（F-PM02 / PM-02）
 */
@Schema(description = "管理后台 - 项目子树移动 Request VO")
@Data
public class ProjectTreeMoveReqVO {

    @Schema(description = "目标父项目ID（移动后挂到其直接下级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目标父项目不能为空")
    private Long newParentId;
}
