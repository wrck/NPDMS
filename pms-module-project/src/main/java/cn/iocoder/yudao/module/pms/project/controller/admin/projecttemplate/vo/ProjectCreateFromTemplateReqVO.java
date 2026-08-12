package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 从模板创建项目 Request VO")
@Data
public class ProjectCreateFromTemplateReqVO {

    @Schema(description = "项目模板编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目模板编号不能为空")
    private Long templateId;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PMS202608001")
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目A")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "合同编码", example = "HT202608001")
    @Size(max = 64, message = "合同编码长度不能超过 64 个字符")
    private String contractCode;

    @Schema(description = "来源系统", requiredMode = Schema.RequiredMode.REQUIRED, example = "MANUAL")
    @NotBlank(message = "来源系统不能为空")
    @Size(max = 64, message = "来源系统长度不能超过 64 个字符")
    private String sourceSystem;

    @Schema(description = "来源业务键", requiredMode = Schema.RequiredMode.REQUIRED, example = "MANUAL-PMS202608001")
    @NotBlank(message = "来源业务键不能为空")
    @Size(max = 128, message = "来源业务键长度不能超过 128 个字符")
    private String sourceBusinessKey;

    @Schema(description = "项目经理用户编号", example = "1")
    private Long managerUserId;
}
