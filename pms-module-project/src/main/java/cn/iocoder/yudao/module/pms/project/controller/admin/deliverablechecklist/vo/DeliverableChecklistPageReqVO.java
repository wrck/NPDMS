package cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 交付件检查分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverableChecklistPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "交付件编码，模糊匹配", example = "DLV-001")
    private String code;

    @Schema(description = "交付件名称，模糊匹配", example = "验收")
    private String name;

    @Schema(description = "关联验收编号", example = "300")
    private Long acceptanceId;

    @Schema(description = "交付件类型 REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件", example = "REQUIRED")
    private String deliverableType;

    @Schema(description = "状态 PENDING / SUBMITTED / ACCEPTED / REJECTED", example = "PENDING")
    private String status;

}
