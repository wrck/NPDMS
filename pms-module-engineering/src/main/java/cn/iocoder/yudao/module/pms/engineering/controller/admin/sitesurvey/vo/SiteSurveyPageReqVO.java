package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 现场工勘分页 Request VO（FR-ENG-001）。
 */
@Schema(description = "管理后台 - 现场工勘分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "工勘编码", example = "SUR-2026-001")
    private String code;

    @Schema(description = "工勘名称", example = "核心机房")
    private String name;

    @Schema(description = "状态：0 草稿 1 已确认 2 已驳回 3 已归档", example = "0")
    private Integer status;

    @Schema(description = "工勘责任人", example = "1")
    private Long surveyorUserId;
}
