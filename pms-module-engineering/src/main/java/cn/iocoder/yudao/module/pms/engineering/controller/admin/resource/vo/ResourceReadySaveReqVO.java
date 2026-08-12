package cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 资源与备件就绪新增/修改 Request VO（FR-ENG-018）。
 */
@Schema(description = "管理后台 - 资源与备件就绪新增/修改 Request VO")
@Data
public class ResourceReadySaveReqVO {

    @Schema(description = "资源编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "资源编码，项目内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "R20260101001")
    @NotBlank(message = "资源编码不能为空")
    @Size(max = 64, message = "资源编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机备件")
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 128, message = "资源名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "资源类型：PEOPLE 备件 / SPARE 物料 / TOOL 工具 / TEST_ENV 测试环境 / WINDOW 时间窗口 / APPROVAL 客户批准", example = "SPARE")
    @Size(max = 32, message = "资源类型长度不能超过 32 个字符")
    private String resourceType;

    @Schema(description = "关联设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "数量", example = "10")
    private Integer quantity;

    @Schema(description = "备注", example = "需提前一周到货")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
