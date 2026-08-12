package cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理后台 - PMS 授权管理新增/修改 Request VO（FR-ENG-010）。
 */
@Schema(description = "管理后台 - PMS 授权管理新增/修改 Request VO")
@Data
public class AuthorizationSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "授权编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "AUTH-2026-001")
    @NotBlank(message = "授权编号不能为空")
    @Size(max = 64, message = "授权编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "授权名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "FW-2000 临时授权")
    @NotBlank(message = "授权名称不能为空")
    @Size(max = 200, message = "授权名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "授权类型：FORMAL 正式 / TEMPORARY 临时 / LOAN 借货", example = "TEMPORARY")
    @Size(max = 32, message = "授权类型长度不能超过 32 个字符")
    private String authorizationType;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    @Size(max = 64, message = "设备序列号长度不能超过 64 个字符")
    private String deviceSerial;

    @Schema(description = "设备型号")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String deviceModel;

    @Schema(description = "授权密钥")
    @Size(max = 256, message = "授权密钥长度不能超过 256 个字符")
    private String licenseKey;

    @Schema(description = "授权类型描述")
    @Size(max = 64, message = "授权类型描述长度不能超过 64 个字符")
    private String licenseType;

    @Schema(description = "申请开始日期", example = "2026-01-01")
    private LocalDate applyStartDate;

    @Schema(description = "申请结束日期", example = "2026-06-30")
    private LocalDate applyEndDate;

    @Schema(description = "使用次数限制", example = "100")
    private Integer usageLimit;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
