package cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 归档文档创建/修改 Request VO")
@Data
public class ArchiveDocumentSaveReqVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属项目编号不能为空")
    private Long projectId;

    @Schema(description = "归档文档编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ARC-001")
    @NotBlank(message = "归档文档编码不能为空")
    @Size(max = 64, message = "归档文档编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "归档文档名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "验收报告归档")
    @NotBlank(message = "归档文档名称不能为空")
    @Size(max = 128, message = "归档文档名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "文档类型 ACCEPTANCE 验收 / BUSINESS 业务 / TECHNICAL 技术 / FINANCE 财务 / OTHER 其他", example = "ACCEPTANCE")
    @Size(max = 32, message = "文档类型长度不能超过 32 个字符")
    private String documentType;

    @Schema(description = "文档附件地址")
    @Size(max = 500, message = "文档附件地址长度不能超过 500 个字符")
    private String documentUrl;

    @Schema(description = "文档版本号", example = "v1.0")
    @Size(max = 32, message = "文档版本号长度不能超过 32 个字符")
    private String versionNo;

    @Schema(description = "状态 0草稿 1待归档 2已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
