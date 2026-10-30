package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 实施方案生成草稿 Request VO（FR-ENG-013）。
 * <p>
 * 基于项目编号生成方案草稿，方案编码由系统按项目编码自动生成。
 */
@Schema(description = "管理后台 - 实施方案生成草稿 Request VO")
@Data
public class SolutionGenerateDraftReqVO {

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "方案名称", example = "核心交换机替换方案")
    private String solutionName;
}
