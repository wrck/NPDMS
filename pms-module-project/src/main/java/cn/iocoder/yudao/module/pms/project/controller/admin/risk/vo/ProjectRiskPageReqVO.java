package cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 项目风险分页 Request VO（FR-PROJ-026）。
 */
@Schema(description = "管理后台 - 项目风险分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectRiskPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "风险标题，模糊匹配", example = "需求")
    private String title;

    @Schema(description = "风险等级 HIGH/MEDIUM/LOW", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险类型", example = "需求风险")
    private String riskType;

    @Schema(description = "状态：0 已识别 1 处理中 2 已关闭 3 已发生", example = "0")
    private Integer status;

    @Schema(description = "风险负责人用户编号", example = "1")
    private Long ownerUserId;
}
