package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 项目合并 Request VO（FR-PROJ-003）。
 * <p>
 * 将多个源项目合并到目标项目：源项目的子项目挂接到目标项目下，源项目逻辑删除。
 * 目标项目必须存在且不能位于任一源项目子树内。
 */
@Schema(description = "管理后台 - 项目合并 Request VO")
@Data
public class ProjectMergeReqVO {

    @Schema(description = "目标项目编号（合并后保留的项目）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "目标项目编号不能为空")
    private Long targetProjectId;

    @Schema(description = "源项目编号列表（将被合并的项目）", requiredMode = Schema.RequiredMode.REQUIRED, example = "[2048, 2049]")
    @NotEmpty(message = "源项目编号列表不能为空")
    private List<Long> sourceProjectIds;

    @Schema(description = "变更原因，写入审计批次", example = "组织结构合并")
    @Size(max = 500, message = "变更原因长度不能超过 500 个字符")
    private String reason;
}
