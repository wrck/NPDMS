package cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 准备数据表单模板新增/修改 Request VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单模板新增/修改 Request VO")
@Data
public class FormTemplateSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "模板编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "FT-2026-001")
    @NotBlank(message = "模板编号不能为空")
    @Size(max = 64, message = "模板编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "网络安全设备标准采集表单")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "产品类型（联动条件）", example = "FIREWALL")
    @Size(max = 64, message = "产品类型长度不能超过 64 个字符")
    private String productType;

    @Schema(description = "表单配置JSON（form-create conf）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "表单配置不能为空")
    private String conf;

    @Schema(description = "表单字段JSON（form-create fields）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "表单字段不能为空")
    private String fields;

    @Schema(description = "模板说明", example = "防火墙产品标准数据采集模板")
    @Size(max = 500, message = "模板说明长度不能超过 500 个字符")
    private String description;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
