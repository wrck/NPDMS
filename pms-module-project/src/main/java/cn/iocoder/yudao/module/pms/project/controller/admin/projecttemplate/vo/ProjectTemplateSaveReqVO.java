package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目模板创建/修改 Request VO")
@Data
public class ProjectTemplateSaveReqVO {

    @Schema(description = "模板编号", example = "1")
    private Long id;

    @Schema(description = "模板编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "TPL-NET-01")
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "网络集成标准模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "适用项目类型", example = "NETWORK_INTEGRATION")
    @Size(max = 64, message = "项目类型长度不能超过 64 个字符")
    private String projectType;

    @Schema(description = "描述", example = "网络集成类项目标准模板")
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "排序号", example = "1")
    private Integer sort;

    @Schema(description = "模板内容快照")
    private TemplateSnapshot snapshotJson;
}
