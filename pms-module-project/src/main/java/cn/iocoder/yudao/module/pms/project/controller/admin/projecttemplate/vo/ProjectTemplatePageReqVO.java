package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 项目模板分页 Request VO（F-PM03）
 */
@Schema(description = "管理后台 - 项目模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplatePageReqVO extends PageParam {

    @Schema(description = "状态：DRAFT草稿/ACTIVE生效/RETIRED停用", example = "DRAFT")
    private String status;

    @Schema(description = "模板编码，模糊匹配", example = "TPL-STD")
    private String code;

    @Schema(description = "模板名称，模糊匹配", example = "标准交付")
    private String name;
}
