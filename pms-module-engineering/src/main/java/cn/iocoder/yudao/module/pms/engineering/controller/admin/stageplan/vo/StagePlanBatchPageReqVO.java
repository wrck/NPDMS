package cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 阶段施工计划批次分页 Request VO（PLN-01/04）。
 */
@Schema(description = "管理后台 - 阶段施工计划批次分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class StagePlanBatchPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "状态：0草稿 1审批中 2已生效 3已驳回", example = "2")
    private Integer status;
}
