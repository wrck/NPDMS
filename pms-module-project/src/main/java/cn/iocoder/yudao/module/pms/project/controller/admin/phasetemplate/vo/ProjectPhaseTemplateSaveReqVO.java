package cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 阶段模板创建/修改 Request VO")
@Data
public class ProjectPhaseTemplateSaveReqVO {

    @Schema(description = "模板编号", example = "1024")
    private Long id;

    @Schema(description = "模板阶段名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求阶段")
    @NotBlank(message = "模板阶段名称不能为空")
    @Size(max = 128, message = "模板阶段名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "模板阶段编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "REQ")
    @NotBlank(message = "模板阶段编码不能为空")
    @Size(max = 64, message = "模板阶段编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "适用项目类型", example = "实施")
    @Size(max = 64, message = "适用项目类型长度不能超过 64 个字符")
    private String projectType;

    @Schema(description = "描述", example = "需求调研与确认阶段")
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "排序号", example = "0")
    private Integer sort;

    @Schema(description = "准入条件", example = "合同已签订")
    @Size(max = 500, message = "准入条件长度不能超过 500 个字符")
    private String entryCriteria;

    @Schema(description = "退出条件", example = "需求文档已评审通过")
    @Size(max = 500, message = "退出条件长度不能超过 500 个字符")
    private String exitCriteria;

    @Schema(description = "负责角色编码", example = "PROJECT_MANAGER")
    @Size(max = 64, message = "负责角色编码长度不能超过 64 个字符")
    private String responsibleRole;

}
