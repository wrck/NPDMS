package cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 初验/终验创建/修改 Request VO")
@Data
@Deprecated(since = "F-ACC-001", forRemoval = false)
public class AcceptanceSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "验收编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACC-001")
    @NotBlank(message = "验收编码不能为空")
    @Size(max = 64, message = "验收编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "验收名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目初验")
    @NotBlank(message = "验收名称不能为空")
    @Size(max = 128, message = "验收名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "验收类型 PRELIMINARY 初验 / FINAL 终验", example = "PRELIMINARY")
    @Size(max = 32, message = "验收类型长度不能超过 32 个字符")
    private String acceptanceType;

    @Schema(description = "验收日期")
    private LocalDate acceptanceDate;

    @Schema(description = "关联交付计划编号", example = "300")
    private Long planId;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "状态 0草稿 1待提交 2审批中 3已通过 4已驳回 5已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
