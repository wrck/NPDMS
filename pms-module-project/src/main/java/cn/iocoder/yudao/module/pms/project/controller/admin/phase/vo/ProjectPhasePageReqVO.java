package cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 项目阶段分页 Request VO（FR-PROJ-017）。
 */
@Schema(description = "管理后台 - 项目阶段分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPhasePageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "阶段名称，模糊匹配", example = "需求")
    private String name;

    @Schema(description = "阶段编码", example = "PH-REQ")
    private String code;

    @Schema(description = "状态：0 未开始 1 进行中 2 已完成 3 已跳过", example = "0")
    private Integer status;
}
