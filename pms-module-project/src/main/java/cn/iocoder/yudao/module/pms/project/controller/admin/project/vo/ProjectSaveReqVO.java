package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 项目创建/修改 Request VO")
@Data
public class ProjectSaveReqVO {

    @Schema(description = "项目编号", example = "1024")
    private Long id;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PMS202401001")
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目A")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "合同编码", example = "HT202401001")
    @Size(max = 64, message = "合同编码长度不能超过 64 个字符")
    private String contractCode;

    @Schema(description = "所属办公室编号", example = "1")
    private Long officeId;

    @Schema(description = "销售人员编号", example = "1")
    private Long salesUserId;

    @Schema(description = "行业", example = "制造业")
    @Size(max = 64, message = "行业长度不能超过 64 个字符")
    private String industry;

    @Schema(description = "实施方式", example = "自营")
    @Size(max = 64, message = "实施方式长度不能超过 64 个字符")
    private String implementationMode;

    @Schema(description = "项目类型", example = "实施")
    @Size(max = 64, message = "项目类型长度不能超过 64 个字符")
    private String projectType;

    @Schema(description = "出货状态", example = "未出货")
    @Size(max = 64, message = "出货状态长度不能超过 64 个字符")
    private String shipmentStatus;

    @Schema(description = "来源系统", requiredMode = Schema.RequiredMode.REQUIRED, example = "ERP")
    @NotBlank(message = "来源系统不能为空")
    @Size(max = 64, message = "来源系统长度不能超过 64 个字符")
    private String sourceSystem;

    @Schema(description = "来源业务键", requiredMode = Schema.RequiredMode.REQUIRED, example = "ERP-001")
    @NotBlank(message = "来源业务键不能为空")
    @Size(max = 128, message = "来源业务键长度不能超过 128 个字符")
    private String sourceBusinessKey;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "来源项目模板编号", example = "1")
    private Long templateId;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
