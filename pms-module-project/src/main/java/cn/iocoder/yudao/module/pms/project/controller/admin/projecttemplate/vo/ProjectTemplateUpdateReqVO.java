package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 项目模板编辑 Request VO（F-PM03 BR-3）
 * <p>
 * 编辑模板身份与草稿内容（仅草稿工作副本可编辑，已发布版本只读）；
 * 编码不可修改（BR-1）。content 为空时仅编辑身份字段。
 */
@Schema(description = "管理后台 - 项目模板编辑 Request VO")
@Data
public class ProjectTemplateUpdateReqVO {

    @Schema(description = "模板名称", example = "标准交付模板")
    @Size(max = 128, message = "模板名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "匹配优先级（数值小者先命中）", example = "100")
    private Integer matchPriority;

    @Schema(description = "业务场景描述", example = "适用于标准签约交付项目")
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    @Schema(description = "草稿内容（四维条件+流程引用+六类定义行整体替换），为空时不编辑内容")
    @Valid
    private TemplateDefinitionContent content;
}
