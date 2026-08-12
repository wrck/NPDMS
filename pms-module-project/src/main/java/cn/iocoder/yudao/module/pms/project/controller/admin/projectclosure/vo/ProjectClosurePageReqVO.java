package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目闭环分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectClosurePageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "闭环编码，模糊匹配", example = "CL-001")
    private String code;

    @Schema(description = "闭环名称，模糊匹配", example = "闭环")
    private String name;

    @Schema(description = "闭环类型 NORMAL 正常闭环 / CONDITIONAL 带条件移交", example = "NORMAL")
    private String closureType;

    @Schema(description = "状态 0草稿 1待审批 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

}
