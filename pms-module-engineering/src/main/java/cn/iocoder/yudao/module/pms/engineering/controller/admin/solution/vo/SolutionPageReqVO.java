package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 实施方案分页 Request VO（FR-ENG-011 / FR-ENG-013）。
 */
@Schema(description = "管理后台 - 实施方案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SolutionPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "方案编码", example = "SOL-2026-001")
    private String code;

    @Schema(description = "方案名称", example = "核心交换机")
    private String name;

    @Schema(description = "方案类型", example = "IMPLEMENTATION")
    private String solutionType;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "审核级别 0 普通 1 重大", example = "0")
    private Integer reviewLevel;
}
