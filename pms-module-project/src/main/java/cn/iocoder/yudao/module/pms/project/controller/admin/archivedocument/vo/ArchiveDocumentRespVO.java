package cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 归档文档 Response VO")
@Data
public class ArchiveDocumentRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;

    @Schema(description = "归档文档编码，项目内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ARC-001")
    private String code;

    @Schema(description = "归档文档名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "验收报告归档")
    private String name;

    @Schema(description = "文档类型 ACCEPTANCE 验收 / BUSINESS 业务 / TECHNICAL 技术 / FINANCE 财务 / OTHER 其他", example = "ACCEPTANCE")
    private String documentType;

    @Schema(description = "文档附件地址")
    private String documentUrl;

    @Schema(description = "文档版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "归档人", example = "500")
    private Long archiveUserId;

    @Schema(description = "归档时间")
    private LocalDateTime archiveTime;

    @Schema(description = "状态 0草稿 1待归档 2已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
