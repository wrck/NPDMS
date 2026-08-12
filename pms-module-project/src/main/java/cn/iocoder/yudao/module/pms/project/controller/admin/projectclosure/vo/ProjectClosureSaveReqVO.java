package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目闭环创建/修改 Request VO")
@Data
public class ProjectClosureSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "闭环编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "CL-001")
    @NotBlank(message = "闭环编码不能为空")
    @Size(max = 64, message = "闭环编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "闭环名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目闭环")
    @NotBlank(message = "闭环名称不能为空")
    @Size(max = 128, message = "闭环名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "闭环类型 NORMAL 正常闭环 / CONDITIONAL 带条件移交", example = "NORMAL")
    @Size(max = 32, message = "闭环类型长度不能超过 32 个字符")
    private String closureType;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "遗留问题摘要")
    private String legacyIssueSummary;

    @Schema(description = "状态 0草稿 1待审批 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
