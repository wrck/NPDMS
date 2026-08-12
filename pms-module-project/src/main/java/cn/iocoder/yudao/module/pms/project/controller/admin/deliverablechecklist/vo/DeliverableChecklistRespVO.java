package cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 交付件检查 Response VO")
@Data
public class DeliverableChecklistRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "交付件编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "DLV-001")
    private String code;

    @Schema(description = "交付件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "验收报告")
    private String name;

    @Schema(description = "关联验收编号", example = "300")
    private Long acceptanceId;

    @Schema(description = "交付件类型 REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件", example = "REQUIRED")
    private String deliverableType;

    @Schema(description = "交付件附件地址")
    private String deliverableUrl;

    @Schema(description = "检查人", example = "500")
    private Long checkUserId;

    @Schema(description = "检查时间")
    private LocalDateTime checkTime;

    @Schema(description = "检查结果")
    private String checkResult;

    @Schema(description = "状态 0草稿 1已提交 2已通过 3已驳回", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
