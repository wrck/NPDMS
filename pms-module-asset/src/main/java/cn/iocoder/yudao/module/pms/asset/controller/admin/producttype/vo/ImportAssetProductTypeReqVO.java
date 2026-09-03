package cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 产品类型受控导入 Request VO")
@Data
public class ImportAssetProductTypeReqVO {

    @NotBlank(message = "操作ID不能为空")
    @Size(max = 128, message = "操作ID不能超过128个字符")
    private String operationId;

    @NotBlank(message = "产品类型编码不能为空")
    @Size(max = 64, message = "产品类型编码不能超过64个字符")
    private String productTypeCode;

    @NotBlank(message = "产品类型名称不能为空")
    @Size(max = 128, message = "产品类型名称不能超过128个字符")
    private String displayName;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    @NotBlank(message = "来源系统不能为空")
    @Size(max = 32, message = "来源系统不能超过32个字符")
    private String sourceSystem;

    @NotBlank(message = "来源键不能为空")
    @Size(max = 128, message = "来源键不能超过128个字符")
    private String sourceKey;

    @NotBlank(message = "来源版本不能为空")
    @Size(max = 128, message = "来源版本不能超过128个字符")
    private String sourceVersion;

    @NotNull(message = "来源更新时间不能为空")
    private LocalDateTime sourceUpdatedAt;

    @NotBlank(message = "载荷摘要不能为空")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "载荷摘要必须为64位十六进制")
    private String payloadHash;

    @Valid
    private List<DeviceCurrentProductTypeReqVO> devices;
}
