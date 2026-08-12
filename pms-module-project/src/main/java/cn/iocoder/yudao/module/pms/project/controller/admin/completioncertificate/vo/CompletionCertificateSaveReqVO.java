package cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 电子完工证明创建/修改 Request VO")
@Data
public class CompletionCertificateSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "完工证明编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "CC-001")
    @NotBlank(message = "完工证明编码不能为空")
    @Size(max = 64, message = "完工证明编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "完工证明名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目完工证明")
    @NotBlank(message = "完工证明名称不能为空")
    @Size(max = 128, message = "完工证明名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "证明编号（业务编号）", example = "CERT-2026-001")
    @Size(max = 64, message = "证明编号长度不能超过 64 个字符")
    private String certificateNo;

    @Schema(description = "客户编号", example = "200")
    private Long customerId;

    @Schema(description = "完工日期")
    private LocalDate completionDate;

    @Schema(description = "完工证明内容")
    private String content;

    @Schema(description = "附件地址")
    @Size(max = 500, message = "附件地址长度不能超过 500 个字符")
    private String attachmentUrl;

    @Schema(description = "状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回", example = "0")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
