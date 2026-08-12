package cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 团队批量变更分页 Request VO（FR-PROJ-014）。
 */
@Schema(description = "管理后台 - 团队批量变更分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamBatchChangePageReqVO extends PageParam {

    @Schema(description = "批次编号", example = "BC20260730001")
    private String batchNo;

    @Schema(description = "源用户编号", example = "1")
    private Long sourceUserId;

    @Schema(description = "目标用户编号", example = "2")
    private Long targetUserId;

    @Schema(description = "范围类型：ALL / SELECTED", example = "SELECTED")
    private String scopeType;

    @Schema(description = "状态：0处理中 1成功 2部分成功 3失败", example = "0")
    private Integer status;
}
