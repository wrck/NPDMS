package cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 准备数据表单实例新增/修改 Request VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单实例新增/修改 Request VO")
@Data
public class FormInstanceSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "实例编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "FI-2026-001")
    @NotBlank(message = "实例编号不能为空")
    @Size(max = 64, message = "实例编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "关联模板ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "关联模板ID不能为空")
    private Long templateId;

    @Schema(description = "模板快照JSON（版本固定到实例）")
    private String templateSnapshot;

    @Schema(description = "填报数据JSON")
    private String formData;

    @Schema(description = "实例名称", example = "XX项目防火墙采集表")
    @Size(max = 200, message = "实例名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "填报人", example = "1024")
    private Long fillerUserId;

    @Schema(description = "备注", example = "现场数据已采集")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
