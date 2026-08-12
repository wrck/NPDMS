package cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 工期倒排分页 Request VO（FR-PROJ-018）。
 */
@Schema(description = "管理后台 - 工期倒排分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleBackwardPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "100")
    private Long projectId;

    @Schema(description = "项目类型：DIRECT / INDIRECT", example = "DIRECT")
    private String projectType;

    @Schema(description = "状态：0草稿 1已计算 2已应用 3已驳回", example = "0")
    private Integer status;
}
