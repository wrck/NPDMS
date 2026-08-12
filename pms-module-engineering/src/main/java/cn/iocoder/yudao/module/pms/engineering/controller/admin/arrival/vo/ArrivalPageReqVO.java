package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 到货签收分页 Request VO（FR-ENG-021）。
 */
@Schema(description = "管理后台 - 到货签收分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArrivalPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "签收编码", example = "ARR-2026-001")
    private String code;

    @Schema(description = "状态：0 待签收 1 已签收 2 异常", example = "0")
    private Integer status;

    @Schema(description = "关联设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "签收人", example = "1")
    private Long receiverUserId;
}
