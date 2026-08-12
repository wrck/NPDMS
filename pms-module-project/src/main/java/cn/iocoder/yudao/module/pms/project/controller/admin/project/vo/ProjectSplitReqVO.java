package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 项目拆分 Request VO（FR-PROJ-003）。
 * <p>
 * 将源项目拆分为多个下挂子项目；子项目自动继承源项目客户与树结构。
 */
@Schema(description = "管理后台 - 项目拆分 Request VO")
@Data
public class ProjectSplitReqVO {

    @Schema(description = "源项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "源项目编号不能为空")
    private Long sourceProjectId;

    @Schema(description = "拆分子项目列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "拆分子项目列表不能为空")
    @Valid
    private List<SplitItem> items;

    @Schema(description = "变更原因，写入审计批次", example = "项目范围拆分")
    @Size(max = 500, message = "变更原因长度不能超过 500 个字符")
    private String reason;

    @Data
    public static class SplitItem {
        @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "P20260101002")
        @NotBlank(message = "项目编码不能为空")
        @Size(max = 64, message = "项目编码长度不能超过 64 个字符")
        private String code;

        @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "某交付项目一期-A 标段")
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
        private String name;

        @Schema(description = "同级排序号", example = "0")
        private Integer sort;

        @Schema(description = "项目分类", example = "交付类")
        @Size(max = 64, message = "项目分类长度不能超过 64 个字符")
        private String category;
    }
}
