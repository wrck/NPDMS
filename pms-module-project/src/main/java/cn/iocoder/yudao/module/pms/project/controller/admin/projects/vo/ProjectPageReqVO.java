package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目分页 Request VO（名称/编码/状态/三维过滤）
 */
@Schema(description = "管理后台 - 项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "项目名称（模糊）", example = "网络优化")
    private String projectName;

    @Schema(description = "项目编码", example = "PJT2026000001")
    private String projectCode;

    @Schema(description = "项目状态", example = "S0")
    private String status;

    @Schema(description = "签约方式", example = "DIRECT")
    private String signingMethod;

    @Schema(description = "项目类别", example = "ENGINEERING")
    private String projectCategory;

    @Schema(description = "实施方式", example = "FACTORY_SERVICE")
    private String implementationMode;
}
