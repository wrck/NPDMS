package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 需求分析分页 Request VO（FR-ENG-004）。
 */
@Schema(description = "管理后台 - 需求分析分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "需求编码", example = "REQ-2026-001")
    private String code;

    @Schema(description = "需求名称", example = "核心网")
    private String name;

    @Schema(description = "需求类型 BUSINESS / INTERFACE", example = "BUSINESS")
    private String requirementType;

    @Schema(description = "状态：0 草稿 1 已提交 2 已生效 3 已归档", example = "0")
    private Integer status;
}
