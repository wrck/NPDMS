package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 管理后台 - 项目模板详情 Response VO（F-PM03）
 * <p>
 * 模板身份和版本清单；设计模型由草稿接口提供，不附带第二份旧模型投影。
 */
@Schema(description = "管理后台 - 项目模板详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDetailRespVO extends ProjectTemplateRespVO {

    @Schema(description = "版本清单（版本号倒序）")
    private List<ProjectTemplateRevisionRespVO> revisions;
}
