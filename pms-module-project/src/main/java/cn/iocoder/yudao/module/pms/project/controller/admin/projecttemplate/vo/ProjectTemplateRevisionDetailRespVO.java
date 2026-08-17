package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 项目模板已发布版本详情 Response VO（F-PM03 BR-3 只读快照）
 */
@Schema(description = "管理后台 - 项目模板已发布版本详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateRevisionDetailRespVO extends ProjectTemplateRevisionRespVO {

    @Schema(description = "版本完整内容快照（只读）")
    private TemplateDefinitionContent content;
}
