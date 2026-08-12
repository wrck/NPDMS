package cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 单机风险新增/修改 Request VO（FR-ENG-008）。
 */
@Schema(description = "管理后台 - PMS 单机风险新增/修改 Request VO")
@Data
public class RiskSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1024")
    private Long id;

    @Schema(description = "风险编号，全局唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "RK-2026-001")
    @NotBlank(message = "风险编号不能为空")
    @Size(max = 64, message = "风险编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "风险名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "单机部署风险")
    @NotBlank(message = "风险名称不能为空")
    @Size(max = 200, message = "风险名称长度不能超过 200 个字符")
    private String name;

    @Schema(description = "风险类型：SINGLE_DEVICE 单机 / SCENARIO 场景", example = "SINGLE_DEVICE")
    @Size(max = 32, message = "风险类型长度不能超过 32 个字符")
    private String riskType;

    @Schema(description = "关联设备ID", example = "1024")
    private Long deviceId;

    @Schema(description = "设备序列号")
    @Size(max = 64, message = "设备序列号长度不能超过 64 个字符")
    private String deviceSerial;

    @Schema(description = "设备型号")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String deviceModel;

    @Schema(description = "风险场景描述")
    private String scenario;

    @Schema(description = "风险等级：HIGH / MEDIUM / LOW", example = "HIGH")
    @Size(max = 16, message = "风险等级长度不能超过 16 个字符")
    private String riskLevel;

    @Schema(description = "处理人", example = "1024")
    private Long handlerUserId;

    @Schema(description = "创建人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
