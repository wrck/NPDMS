package cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 电子完工证明 Response VO")
@Data
public class CompletionCertificateRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "完工证明编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "CC-001")
    private String code;

    @Schema(description = "完工证明名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX项目完工证明")
    private String name;

    @Schema(description = "证明编号（业务编号）", example = "CERT-2026-001")
    private String certificateNo;

    @Schema(description = "客户编号", example = "200")
    private Long customerId;

    @Schema(description = "完工日期")
    private LocalDate completionDate;

    @Schema(description = "客户确认人", example = "500")
    private Long customerConfirmUserId;

    @Schema(description = "客户确认时间")
    private LocalDateTime customerConfirmTime;

    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "完工证明内容")
    private String content;

    @Schema(description = "附件地址")
    private String attachmentUrl;

    @Schema(description = "状态 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
