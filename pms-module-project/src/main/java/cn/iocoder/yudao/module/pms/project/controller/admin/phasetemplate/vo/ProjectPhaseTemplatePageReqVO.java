package cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 阶段模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPhaseTemplatePageReqVO extends PageParam {

    @Schema(description = "模板阶段编码，模糊匹配", example = "REQ")
    private String code;

    @Schema(description = "模板阶段名称，模糊匹配", example = "需求")
    private String name;

    @Schema(description = "适用项目类型", example = "实施")
    private String projectType;

    @Schema(description = "状态：0启用 1停用", example = "0")
    private Integer status;

}
