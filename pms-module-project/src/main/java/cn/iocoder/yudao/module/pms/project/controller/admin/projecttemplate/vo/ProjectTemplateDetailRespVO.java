package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 管理后台 - 项目模板详情 Response VO（F-PM03）
 * <p>
 * 模板身份 + 当前草稿内容 + 版本清单（版本号倒序，最新 PUBLISHED 在首）。
 */
@Schema(description = "管理后台 - 项目模板详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDetailRespVO extends ProjectTemplateRespVO {

    @Schema(description = "当前草稿内容（含四维条件与六类定义行；RETIRED 模板无草稿则为 null）")
    private TemplateDefinitionContent draftContent;

    @Schema(description = "版本清单（版本号倒序）")
    private List<ProjectTemplateRevisionRespVO> revisions;
}
