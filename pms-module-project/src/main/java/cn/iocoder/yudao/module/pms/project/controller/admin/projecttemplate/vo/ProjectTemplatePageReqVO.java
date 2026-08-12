package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplatePageReqVO extends PageParam {

    @Schema(description = "模板编码", example = "TPL")
    private String code;

    @Schema(description = "模板名称", example = "网络集成")
    private String name;

    @Schema(description = "项目类型", example = "NETWORK_INTEGRATION")
    private String projectType;

    @Schema(description = "状态：0启用 1停用", example = "0")
    private Integer status;
}
