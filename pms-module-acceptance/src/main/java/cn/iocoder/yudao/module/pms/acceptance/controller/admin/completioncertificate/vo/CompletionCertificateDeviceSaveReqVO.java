package cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 电子完工证明设备明细创建/修改 Request VO")
@Data
public class CompletionCertificateDeviceSaveReqVO {

    @Schema(description = "设备类型", example = "智能电网安全防护设备")
    @Size(max = 128, message = "设备类型长度不能超过 128 个字符")
    private String deviceType;

    @Schema(description = "设备型号", example = "DPtech-LPH-9000")
    @Size(max = 128, message = "设备型号长度不能超过 128 个字符")
    private String deviceModel;

    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;

}
