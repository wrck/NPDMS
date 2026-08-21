package cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 交付件检查创建/修改 Request VO")
@Data
public class DeliverableChecklistSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "交付件编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "DLV-001")
    @NotBlank(message = "交付件编码不能为空")
    @Size(max = 64, message = "交付件编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "交付件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "验收报告")
    @NotBlank(message = "交付件名称不能为空")
    @Size(max = 128, message = "交付件名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "关联验收编号", example = "300")
    private Long acceptanceId;

    @Schema(description = "交付件类型 REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件", example = "REQUIRED")
    @Size(max = 32, message = "交付件类型长度不能超过 32 个字符")
    private String deliverableType;

    @Schema(description = "交付件附件地址")
    @Size(max = 500, message = "交付件附件地址长度不能超过 500 个字符")
    private String deliverableUrl;

    @Schema(description = "检查结果")
    private String checkResult;

    @Schema(description = "状态 PENDING / SUBMITTED / ACCEPTED / REJECTED", example = "PENDING")
    private String status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
