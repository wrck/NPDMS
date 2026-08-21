package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 项目更新 Request VO（BR-7 仅暴露可编辑属性：名称/客户/合同号/实施地点；
 * 编码、父节点、来源、模板绑定、状态不可改，载荷不含这些字段）
 */
@Schema(description = "管理后台 - 项目更新 Request VO")
@Data
public class ProjectUpdateReqVO {

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目ID不能为空")
    private Long id;

    @Schema(description = "项目名称", example = "某客户网络优化工程")
    private String projectName;

    @Schema(description = "客户编码", example = "CUS-001")
    private String customerCode;

    @Schema(description = "客户名称", example = "某公司")
    private String customerName;

    @Schema(description = "手工登记合同号", example = "HT-2026-001")
    private String contractNo;

    @Schema(description = "实施地点", example = "上海")
    private String implementationLocation;
}
