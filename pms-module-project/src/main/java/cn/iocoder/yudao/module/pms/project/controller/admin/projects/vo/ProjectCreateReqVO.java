package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 手工创建项目 Request VO（F-PM01 / PM-01，BR-2 必填：名称/三维/创建原因）
 */
@Schema(description = "管理后台 - 项目手工创建 Request VO")
@Data
public class ProjectCreateReqVO {

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "某客户网络优化工程")
    @NotEmpty(message = "项目名称不能为空")
    private String projectName;

    @Schema(description = "客户编码", example = "CUS-001")
    private String customerCode;

    @Schema(description = "客户名称", example = "某公司")
    private String customerName;

    @Schema(description = "手工登记合同号", example = "HT-2026-001")
    private String contractNo;

    @Schema(description = "下单办事处公司编码（登记 ORDER_OFFICE 关系）", example = "COMP-SH")
    private String orderOfficeCompanyCode;

    @Schema(description = "下单办事处部门编码", example = "DEPT-SH-01")
    private String orderOfficeDepartmentCode;

    @Schema(description = "实施地点", example = "上海")
    private String implementationLocation;

    @Schema(description = "签约方式（字典 pms_signing_method）", example = "DIRECT")
    private String signingMethod;

    @Schema(description = "项目类别（字典 pms_project_category）", example = "ENGINEERING")
    private String projectCategory;

    @Schema(description = "实施方式（字典 pms_implementation_method）", example = "FACTORY_SERVICE")
    private String implementationMode;

    @Schema(description = "重大项目级别（CRM 来源属性映射，空=不限）", example = "")
    private String majorProjectLevel;

    @Schema(description = "手工创建原因（BR-2 必填）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "手工创建原因不能为空")
    private String creationReason;

    @Schema(description = "人工选择模板ID（空=四维自动匹配，多匹配/无匹配时阻断）", example = "910001")
    private Long templateId;

    @Schema(description = "可选一级服务经理用户ID（空=创建后人工指派）", example = "1")
    private Long serviceManagerUserId;
}
