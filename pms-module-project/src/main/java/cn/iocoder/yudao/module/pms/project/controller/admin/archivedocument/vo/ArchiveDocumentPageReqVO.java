package cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 归档文档分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveDocumentPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "归档文档编码，模糊匹配", example = "ARC-001")
    private String code;

    @Schema(description = "归档文档名称，模糊匹配", example = "验收")
    private String name;

    @Schema(description = "文档类型 ACCEPTANCE 验收 / BUSINESS 业务 / TECHNICAL 技术 / FINANCE 财务 / OTHER 其他", example = "ACCEPTANCE")
    private String documentType;

    @Schema(description = "状态 0草稿 1待归档 2已归档", example = "0")
    private Integer status;

}
